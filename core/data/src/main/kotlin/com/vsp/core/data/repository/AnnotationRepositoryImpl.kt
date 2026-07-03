package com.vsp.core.data.repository

import com.vsp.core.data.local.dao.AnnotationDao
import com.vsp.core.data.mapper.toDomain
import com.vsp.core.data.mapper.toEntity
import com.vsp.core.domain.coroutine.DispatcherProvider
import com.vsp.core.domain.repository.AnnotationRepository
import com.vsp.core.model.Annotation
import com.vsp.core.model.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnnotationRepositoryImpl @Inject constructor(
    private val annotationDao: AnnotationDao,
    private val dispatchers: DispatcherProvider,
) : AnnotationRepository {

    override fun observeAnnotations(imageId: String): Flow<List<Annotation>> =
        annotationDao.observeForImage(imageId).map { list -> list.map { it.toDomain() } }

    override suspend fun add(annotation: Annotation): AppResult<Annotation> =
        withContext(dispatchers.io) {
            annotationDao.insert(annotation.toEntity())
            AppResult.Success(annotation)
        }

    override suspend fun update(annotation: Annotation): AppResult<Unit> =
        withContext(dispatchers.io) {
            annotationDao.update(annotation.copy(updatedAt = System.currentTimeMillis()).toEntity())
            AppResult.Success(Unit)
        }

    override suspend fun delete(annotationId: String): AppResult<Unit> =
        withContext(dispatchers.io) {
            annotationDao.delete(annotationId)
            AppResult.Success(Unit)
        }
}
