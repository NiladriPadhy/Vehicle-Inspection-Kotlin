package com.vsp.core.data.repository

import com.vsp.core.data.local.dao.ChecklistResponseDao
import com.vsp.core.data.mapper.toDomain
import com.vsp.core.data.mapper.toEntity
import com.vsp.core.domain.coroutine.DispatcherProvider
import com.vsp.core.domain.repository.ChecklistRepository
import com.vsp.core.model.AppError
import com.vsp.core.model.AppResult
import com.vsp.core.model.ChecklistResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChecklistRepositoryImpl @Inject constructor(
    private val checklistResponseDao: ChecklistResponseDao,
    private val dispatchers: DispatcherProvider,
) : ChecklistRepository {

    override fun observeResponses(inspectionId: String): Flow<List<ChecklistResponse>> =
        checklistResponseDao.observeForInspection(inspectionId).map { list -> list.map { it.toDomain() } }

    override suspend fun save(response: ChecklistResponse): AppResult<Unit> = withContext(dispatchers.io) {
        runCatching { checklistResponseDao.upsert(response.toEntity()) }.fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { AppResult.Failure(AppError.Storage(it.message ?: "Failed to save checklist item")) },
        )
    }
}
