package com.red.server.api

/**
 * مُعطّل: كان يكرر mapping `/api/calls/v2` الموجود في
 * `com.red.server.calls.ModernCallsController` (كانا يتسابقان على نفس المسارات:
 * POST /start و/{callId}/answer و/reject و/end و/types و/stats).
 *
 * العقد الوحيد المعتمد هو ModernCallsController:
 * - الهوية من Authentication (لا X-RED-ID)،
 * - التخزين الدائم عبر CallHistoryService (Mongo call_history)،
 * - التسليم عبر UnifiedCallDeliveryService.
 *
 * أُبقي هذا الملف بلا أي ستيريوتايب Spring (@RestController/@RequestMapping
 * محذوفتان عمداً) حتى لا يسجَّل أي مسار — حذفه النهائي بيد مالك العقد بعد
 * تأكيد عدم اعتماد أي عميل على شكله القديم (in-memory + X-RED-ID).
 *
 * لا تعيد تفعيل هذا المتحكم بإضافة @RestController.
 */
@Deprecated("Duplicate of ModernCallsController — canonical contract lives there.")
class UnifiedCallsControllerV2Disabled
