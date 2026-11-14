package com.tallybook.core.data.mapper

import com.tallybook.core.database.entity.AccountEntity
import com.tallybook.core.database.entity.BudgetEntity
import com.tallybook.core.database.entity.CategoryEntity
import com.tallybook.core.database.entity.TransactionEntity
import com.tallybook.core.model.money.CurrencyCode
import com.tallybook.core.model.money.Money
import com.tallybook.core.model.transaction.Account
import com.tallybook.core.model.transaction.Budget
import com.tallybook.core.model.transaction.Category
import com.tallybook.core.model.transaction.Transaction
import com.tallybook.core.model.transaction.TransactionId

/**
 * Entity <-> domain mapping.
 *
 * Hand-written extension functions rather than a mapping library: the mapping is small, fully
 * type-checked, and reflection-free. A renamed field breaks the build instead of silently producing
 * nulls at runtime — which is the entire point of keeping the DB and domain models separate.
 */

fun TransactionEntity.toDomain(tags: List<String> = emptyList()): Transaction = Transaction(
    id = TransactionId(id),
    type = type,
    kind = kind,
    amount = Money(amountMinor, CurrencyCode(currency)),
    accountId = accountId,
    categoryId = categoryId,
    occurredAt = occurredAt,
    note = note,
    tags = tags,
    counterAccountId = counterAccountId,
    refundOfId = refundOfId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    id = id.value,
    type = type,
    kind = kind,
    amountMinor = amount.minorUnits,
    currency = amount.currency.value,
    accountId = accountId,
    categoryId = categoryId,
    occurredAt = occurredAt,
    note = note,
    counterAccountId = counterAccountId,
    refundOfId = refundOfId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun AccountEntity.toDomain(): Account = Account(
    id = id,
    name = name,
    type = type,
    currency = CurrencyCode(currency),
    initialBalance = Money(initialBalanceMinor, CurrencyCode(currency)),
    isArchived = isArchived,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Account.toEntity(now: Long): AccountEntity = AccountEntity(
    id = id,
    name = name,
    type = type,
    currency = currency.value,
    initialBalanceMinor = initialBalance.minorUnits,
    isArchived = isArchived,
    sortOrder = sortOrder,
    createdAt = createdAt.takeIf { it > 0 } ?: now,
    updatedAt = now,
)

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    type = type,
    parentId = parentId,
    iconKey = iconKey,
    colorArgb = colorArgb,
    sortOrder = sortOrder,
    isArchived = isArchived,
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    type = type,
    parentId = parentId,
    iconKey = iconKey,
    colorArgb = colorArgb,
    sortOrder = sortOrder,
    isArchived = isArchived,
)

fun BudgetEntity.toDomain(): Budget = Budget(
    id = id,
    name = name,
    categoryId = categoryId,
    period = period,
    limit = Money(limitMinor, CurrencyCode(currency)),
    includeSubcategories = includeSubcategories,
    isActive = isActive,
    createdAt = createdAt,
)

/**
 * Budget entity from a domain model.
 *
 * [createdAtFallback] is used only when a brand-new row has no timestamp yet; the DAO's `@Upsert`
 * preserves the existing column value on update, so an edit never overwrites the original creation
 * time with "now".
 */
fun Budget.toEntity(createdAtFallback: Long = 0L): BudgetEntity = BudgetEntity(
    id = id,
    name = name,
    categoryId = categoryId,
    period = period,
    limitMinor = limit.minorUnits,
    currency = limit.currency.value,
    includeSubcategories = includeSubcategories,
    isActive = isActive,
    createdAt = createdAtFallback,
)
