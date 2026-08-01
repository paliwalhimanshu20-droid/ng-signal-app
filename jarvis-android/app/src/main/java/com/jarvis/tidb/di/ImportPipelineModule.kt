package com.jarvis.tidb.di

import android.content.Context
import com.jarvis.tidb.core.entity.Timeframe
import com.jarvis.tidb.historical.ingestion.datasource.CsvDataSourceProvider
import com.jarvis.tidb.historical.ingestion.datasource.CsvPathResolver
import com.jarvis.tidb.historical.ingestion.datasource.DataSourceProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File

/**
 * "Phase 4A, Section 1 -- Data Source Framework." Real, not placeholder: resolves to
 * `<app files dir>/historical_data/<instrumentId>_<timeframe>.csv`, a genuine convention a real
 * import (or a person copying a file onto the device) can target -- not a stub that would need
 * replacing before this pipeline could ever actually run.
 */
class DefaultCsvPathResolver(private val context: Context) : CsvPathResolver {
    override fun resolve(instrumentId: Long, timeframe: Timeframe): String {
        val dir = File(context.filesDir, "historical_data")
        return File(dir, "${instrumentId}_${timeframe.value}.csv").absolutePath
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ImportPipelineModule {

    @Binds
    abstract fun bindDataSourceProvider(impl: CsvDataSourceProvider): DataSourceProvider

    companion object {
        @Provides
        fun provideCsvPathResolver(@ApplicationContext context: Context): CsvPathResolver = DefaultCsvPathResolver(context)
    }
}
