package dev.aliencode.adapters.mission.persistence;

import java.nio.file.Path;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import org.sqlite.SQLiteDataSource;

/** Um banco SQLite em arquivo temporário, com o schema.sql de produção aplicado. */
final class SqliteTestDatabase {

    final SQLiteDataSource dataSource = new SQLiteDataSource();
    final JdbcClient jdbc;
    final TransactionTemplate transactions;

    SqliteTestDatabase(Path file) {
        this.dataSource.setUrl("jdbc:sqlite:" + file);

        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(this.dataSource);

        this.jdbc = JdbcClient.create(this.dataSource);
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(this.dataSource));
    }
}
