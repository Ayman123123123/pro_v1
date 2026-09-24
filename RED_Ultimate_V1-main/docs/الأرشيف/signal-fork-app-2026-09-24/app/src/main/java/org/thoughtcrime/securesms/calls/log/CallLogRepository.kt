package com.red.sovereign.calls.log

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.concurrent.REDExecutors
import org.signal.core.util.withinTransaction
import com.red.sovereign.calls.links.UpdateCallLinkRepository
import com.red.sovereign.database.CallLinkTable
import com.red.sovereign.database.DatabaseObserver
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobs.CallLogEventSendJob
import com.red.sovereign.service.webrtc.links.CallLinkRoomId
import com.red.sovereign.service.webrtc.links.UpdateCallLinkResult

class CallLogRepository(
  private val updateCallLinkRepository: UpdateCallLinkRepository = UpdateCallLinkRepository(),
  private val callLogPeekHelper: CallLogPeekHelper,
  private val callEventCache: CallEventCache
) : CallLogPagedDataSource.CallRepository {

  companion object {
    fun listenForCallTableChanges(): Observable<Unit> {
      return Observable.create { emitter ->
        fun refresh() {
          emitter.onNext(Unit)
        }

        val databaseObserver = DatabaseObserver.Observer {
          refresh()
        }

        AppDependencies.databaseObserver.registerCallUpdateObserver(databaseObserver)

        emitter.setCancellable {
          AppDependencies.databaseObserver.unregisterObserver(databaseObserver)
        }
      }
    }
  }

  override fun getCallsCount(query: String?, filter: CallLogFilter): Int {
    return callEventCache.getCallEventsCount(CallEventCache.FilterState(query ?: "", filter))
  }

  override fun getCalls(query: String?, filter: CallLogFilter, start: Int, length: Int): List<CallLogRow> {
    return callEventCache.getCallEvents(CallEventCache.FilterState(query ?: "", filter), length, start)
  }

  override fun getCallLinksCount(query: String?, filter: CallLogFilter): Int {
    return when (filter) {
      CallLogFilter.MISSED -> 0
      CallLogFilter.ALL, CallLogFilter.AD_HOC -> REDDatabase.callLinks.getCallLinksCount(query)
    }
  }

  override fun getCallLinks(query: String?, filter: CallLogFilter, start: Int, length: Int): List<CallLogRow> {
    return when (filter) {
      CallLogFilter.MISSED -> emptyList()
      CallLogFilter.ALL, CallLogFilter.AD_HOC -> REDDatabase.callLinks.getCallLinks(query, start, length)
    }
  }

  override fun onCallTabPageLoaded(pageData: List<CallLogRow>) {
    REDExecutors.BOUNDED_IO.execute {
      callLogPeekHelper.onPageLoaded(pageData)
    }
  }

  fun listenForChanges(): Observable<Unit> {
    return callEventCache.listenForChanges()
  }

  fun markAllCallEventsRead() {
    REDExecutors.BOUNDED_IO.execute {
      val latestCall = REDDatabase.calls.getLatestCall() ?: return@execute
      REDDatabase.calls.markAllCallEventsRead()
      AppDependencies.jobManager.add(CallLogEventSendJob.forMarkedAsRead(latestCall))
    }
  }

  fun deleteSelectedCallLogs(
    selectedCallRowIds: Set<Long>
  ): Completable {
    return Completable.fromAction {
      REDDatabase.calls.deleteNonAdHocCallEvents(selectedCallRowIds)
    }.subscribeOn(Schedulers.io())
  }

  fun deleteAllCallLogsExcept(
    selectedCallRowIds: Set<Long>,
    missedOnly: Boolean
  ): Completable {
    return Completable.fromAction {
      REDDatabase.calls.deleteAllNonAdHocCallEventsExcept(selectedCallRowIds, missedOnly)
    }.subscribeOn(Schedulers.io())
  }

  /**
   * Delete all call events / unowned links and enqueue clear history job, and then
   * emit a clear history message.
   *
   * This explicitly drops failed call link revocations of call links, and those call links
   * will remain visible to the user. This is safe because the clear history sync message should
   * only clear local history and then poll link status from the server.
   */
  fun deleteAllCallLogsOnOrBeforeNow(): Single<Int> {
    return Single.fromCallable {
      REDDatabase.rawDatabase.withinTransaction {
        val latestCall = REDDatabase.calls.getLatestCall() ?: return@withinTransaction
        REDDatabase.calls.deleteNonAdHocCallEventsOnOrBefore(latestCall.timestamp)
        REDDatabase.callLinks.deleteNonAdminCallLinksOnOrBefore(latestCall.timestamp)
        AppDependencies.jobManager.add(CallLogEventSendJob.forClearHistory(latestCall))
      }

      REDDatabase.callLinks.getAllAdminCallLinksExcept(emptySet())
    }.flatMap(this::deleteAndCollectResults).map { 0 }.subscribeOn(Schedulers.io())
  }

  /**
   * Deletes the selected call links. We DELETE those links we don't have admin keys for,
   * and revoke the ones we *do* have admin keys for. We then perform a cleanup step on
   * terminate to clean up call events.
   */
  fun deleteSelectedCallLinks(
    selectedCallRowIds: Set<Long>,
    selectedRoomIds: Set<CallLinkRoomId>
  ): Single<Int> {
    return Single.fromCallable {
      val allCallLinkIds = REDDatabase.calls.getCallLinkRoomIdsFromCallRowIds(selectedCallRowIds) + selectedRoomIds
      REDDatabase.callLinks.deleteNonAdminCallLinks(allCallLinkIds)
      REDDatabase.callLinks.getAdminCallLinks(allCallLinkIds)
    }.flatMap(this::deleteAndCollectResults).subscribeOn(Schedulers.io())
  }

  /**
   * Deletes all but the selected call links. We DELETE those links we don't have admin keys for,
   * and revoke the ones we *do* have admin keys for. We then perform a cleanup step on
   * terminate to clean up call events.
   */
  fun deleteAllCallLinksExcept(
    selectedCallRowIds: Set<Long>,
    selectedRoomIds: Set<CallLinkRoomId>
  ): Single<Int> {
    return Single.fromCallable {
      val allCallLinkIds = REDDatabase.calls.getCallLinkRoomIdsFromCallRowIds(selectedCallRowIds) + selectedRoomIds
      REDDatabase.callLinks.deleteAllNonAdminCallLinksExcept(allCallLinkIds)
      REDDatabase.callLinks.getAllAdminCallLinksExcept(allCallLinkIds)
    }.flatMap(this::deleteAndCollectResults).subscribeOn(Schedulers.io())
  }

  private fun deleteAndCollectResults(callLinksToRevoke: Set<CallLinkTable.CallLink>): Single<Int> {
    return Single.merge(
      callLinksToRevoke.map {
        updateCallLinkRepository.deleteCallLink(it.credentials!!)
      }
    ).reduce(0) { acc, current ->
      acc + (if (current is UpdateCallLinkResult.Delete) 0 else 1)
    }.doOnTerminate {
      REDDatabase.calls.updateAdHocCallEventDeletionTimestamps()
    }.doOnDispose {
      REDDatabase.calls.updateAdHocCallEventDeletionTimestamps()
    }
  }
}
