package me.lovelace.loveclaims.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SQLiteShutdownTest {
  @TempDir Path directory;

  @Test
  void shutdownDrainsAcceptedWritesAndFuturesBeforeClosingConnections() throws Exception {
    SQLiteStorage storage = new SQLiteStorage(null);
    String url = "jdbc:sqlite:" + directory.resolve("claims.db");
    Connection connection = DriverManager.getConnection(url);
    set(storage, "claimsConnection", connection);
    Field executorField = SQLiteStorage.class.getDeclaredField("dbExecutor");
    executorField.setAccessible(true);
    ExecutorService executor = (ExecutorService) executorField.get(storage);
    CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
    ExecutorService closer = Executors.newSingleThreadExecutor();
    try {
      executor.submit(
          () -> {
            entered.countDown();
            try {
              release.await();
            } catch (InterruptedException ex) {
              Thread.currentThread().interrupt();
            }
          });
      assertTrue(entered.await(2, TimeUnit.SECONDS));
      CompletableFuture<Void> write =
          CompletableFuture.runAsync(
              () -> {
                try (var statement = connection.createStatement()) {
                  statement.executeUpdate("CREATE TABLE saved (value INTEGER)");
                  statement.executeUpdate("INSERT INTO saved VALUES (42)");
                } catch (Exception ex) {
                  throw new CompletionException(ex);
                }
              },
              executor);
      Future<?> shutdown = closer.submit(storage::close);
      // Synchronize with shutdown, rather than relying on an arbitrary sleep.
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
      while (!executor.isShutdown() && System.nanoTime() < deadline) Thread.yield();
      assertTrue(executor.isShutdown());
      assertFalse(connection.isClosed(), "connection must stay open while a write is queued");
      release.countDown();
      shutdown.get(3, TimeUnit.SECONDS);
      write.get(1, TimeUnit.SECONDS);
      assertTrue(connection.isClosed());
      assertTrue(executor.isTerminated());
      storage.close(); // idempotent
      try (Connection reopened = DriverManager.getConnection(url);
          var statement = reopened.createStatement();
          var rows = statement.executeQuery("SELECT value FROM saved")) {
        assertTrue(rows.next());
        assertEquals(42, rows.getInt(1));
      }
    } finally {
      release.countDown();
      executor.shutdown();
      executor.awaitTermination(3, TimeUnit.SECONDS);
      closer.shutdownNow();
      connection.close();
    }
  }

  private static void set(Object target, String name, Object value) throws Exception {
    Field field = SQLiteStorage.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }
}
