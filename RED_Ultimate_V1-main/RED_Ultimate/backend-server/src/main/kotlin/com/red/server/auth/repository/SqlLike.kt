package com.red.server.auth.repository

/**
 * Builds a SQL LIKE / ILIKE contains pattern without ever sending
 * `LOWER(:param)` to PostgreSQL.
 *
 * Hibernate 6/7 + the Postgres JDBC driver bind an untyped NULL (and
 * sometimes a `CONCAT('%', :search, '%')` argument) as `bytea`. Postgres
 * then fails with `function lower(bytea) does not exist`. The durable
 * fix is: lowercase and wrap the term in the JVM, then compare it to
 * the column. Wildcards in user input are stripped so we do not need
 * an ESCAPE clause that varies across JPQL dialects.
 */
object SqlLike {
    fun contains(raw: String): String {
        val sanitized = buildString(raw.length) {
            raw.lowercase().forEach { ch ->
                if (ch != '%' && ch != '_' && ch != '\\') append(ch)
            }
        }
        return "%$sanitized%"
    }
}
