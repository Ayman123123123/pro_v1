package com.red.server.config

import com.red.server.websocket.CallWebSocketHandler
import com.red.server.websocket.RedMasterHandler
import tools.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.listener.PatternTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter

@Configuration
class RedisPubSubConfig(
    private val callWebSocketHandler: CallWebSocketHandler,
    private val redMasterHandler: RedMasterHandler,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(RedisPubSubConfig::class.java)

    @Bean
    fun redisMessageListenerContainer(
        connectionFactory: RedisConnectionFactory,
        callSignalListenerAdapter: MessageListenerAdapter,
        messageListenerAdapter: MessageListenerAdapter,
        syncEventListenerAdapter: MessageListenerAdapter
    ): RedisMessageListenerContainer {
        val container = RedisMessageListenerContainer()
        container.setConnectionFactory(connectionFactory)
        
        container.addMessageListener(callSignalListenerAdapter, PatternTopic("red:calls:signals"))
        // إشارة تنبيه (wake-up) فقط — الناقل الدائم هو Streams
        // (red:stream:messages:{redId} + consumer-group red-backend-$HOSTNAME).
        container.addMessageListener(messageListenerAdapter, PatternTopic("red:messages:*"))
        container.addMessageListener(syncEventListenerAdapter, PatternTopic("red:sync:events:*"))
        
        return container
    }

    @Bean
    fun callSignalListenerAdapter(): MessageListenerAdapter {
        return MessageListenerAdapter(RedisCallSignalListener(callWebSocketHandler, objectMapper), "receiveMessage")
    }

    @Bean
    fun messageListenerAdapter(): MessageListenerAdapter {
        return MessageListenerAdapter(RedisMessageListener(redMasterHandler), "receiveMessage")
    }

    @Bean
    fun syncEventListenerAdapter(): MessageListenerAdapter {
        return MessageListenerAdapter(RedisSyncEventListener(redMasterHandler), "receiveMessage")
    }
}

class RedisCallSignalListener(
    private val callWebSocketHandler: CallWebSocketHandler,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(RedisCallSignalListener::class.java)

    fun receiveMessage(message: String, channel: String) {
        try {
            callWebSocketHandler.handleRedisSignal(message)
        } catch (e: Exception) {
            log.error("Failed to process Redis call signal", e)
        }
    }
}

class RedisMessageListener(
    private val redMasterHandler: RedMasterHandler
) {
    private val log = LoggerFactory.getLogger(RedisMessageListener::class.java)

    fun receiveMessage(message: String, channel: String) {
        try {
            val receiverId = channel.removePrefix("red:messages:")
            redMasterHandler.deliverMessageFromRedis(receiverId, message)
        } catch (e: Exception) {
            log.error("Failed to process Redis message", e)
        }
    }
}

class RedisSyncEventListener(
    private val redMasterHandler: RedMasterHandler
) {
    private val log = LoggerFactory.getLogger(RedisSyncEventListener::class.java)

    fun receiveMessage(message: String, channel: String) {
        try {
            val receiverId = channel.removePrefix("red:sync:events:")
            redMasterHandler.deliverSyncPingFromRedis(receiverId, message)
        } catch (e: Exception) {
            log.error("Failed to process Redis sync event ping", e)
        }
    }
}
