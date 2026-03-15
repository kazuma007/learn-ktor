package com.visualdiffserver.infrastructure.db

import com.visualdiffserver.config.AppConfig
import com.visualdiffserver.infrastructure.db.tables.ArtifactsTable
import com.visualdiffserver.infrastructure.db.tables.AssetsTable
import com.visualdiffserver.infrastructure.db.tables.ComparisonsTable
import com.visualdiffserver.infrastructure.db.tables.ProjectsTable
import com.visualdiffserver.infrastructure.db.tables.RunsTable
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    private lateinit var database: Database

    fun init(config: AppConfig) {
        database =
            Database.connect(
                url = config.dbUrl,
                user = config.dbUser,
                password = config.dbPassword,
                driver = "org.postgresql.Driver",
            )

        transaction(database) {
            SchemaUtils.create(
                ProjectsTable,
                AssetsTable,
                ComparisonsTable,
                RunsTable,
                ArtifactsTable,
            )
        }
    }

    suspend fun <T> query(block: suspend () -> T): T {
        return newSuspendedTransaction(Dispatchers.IO, db = database) { block() }
    }
}
