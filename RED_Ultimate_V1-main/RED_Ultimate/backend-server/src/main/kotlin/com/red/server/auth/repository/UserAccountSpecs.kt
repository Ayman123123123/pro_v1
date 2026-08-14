package com.red.server.auth.repository

import com.red.server.auth.model.AccountRole
import com.red.server.auth.model.AccountStatus
import com.red.server.auth.model.UserAccount
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Predicate
import org.hibernate.query.criteria.HibernateCriteriaBuilder
import org.springframework.data.jpa.domain.Specification

/**
 * Admin user listing must be built with the Criteria API.
 *
 * A single JPQL string with `(:search IS NULL OR LOWER(...) LIKE LOWER(CONCAT(...)))`
 * is the exact shape that makes Hibernate infer the bind as `bytea` on
 * PostgreSQL. Specifications omit unused predicates, so a missing filter
 * never becomes a typed NULL in SQL.
 *
 * PostgreSQL `ILIKE` is preferred: it uses `idx_users_username_trgm`
 * (gin_trgm_ops) and does not wrap the column in `LOWER()`.
 */
object UserAccountSpecs {
    fun adminSearch(
        status: AccountStatus?,
        role: AccountRole?,
        search: String?,
    ): Specification<UserAccount> = Specification { root, _, cb ->
        val predicates = mutableListOf<Predicate>()
        status?.let { predicates += cb.equal(root.get<AccountStatus>("status"), it) }
        role?.let { predicates += cb.equal(root.get<AccountRole>("role"), it) }
        val term = search?.trim()?.takeIf { it.isNotEmpty() }
        if (term != null) {
            val pattern = SqlLike.contains(term)
            predicates += cb.or(
                containsIgnoreCase(cb, root.get("username"), pattern),
                containsIgnoreCase(cb, root.get("displayName"), pattern),
                containsIgnoreCase(cb, root.get("redId"), pattern),
            )
        }
        cb.and(*predicates.toTypedArray())
    }

    private fun containsIgnoreCase(
        cb: CriteriaBuilder,
        column: Expression<String>,
        pattern: String,
    ): Predicate {
        val hibernate = cb as? HibernateCriteriaBuilder
        return if (hibernate != null) {
            hibernate.ilike(column, pattern)
        } else {
            cb.like(cb.lower(column), pattern)
        }
    }
}
