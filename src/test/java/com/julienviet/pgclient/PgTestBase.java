/*
 * Copyright (C) 2017 Julien Viet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.julienviet.pgclient;

import de.flapdoodle.embed.process.config.IRuntimeConfig;
import de.flapdoodle.embed.process.config.io.ProcessOutput;
import de.flapdoodle.embed.process.runtime.ICommandLinePostProcessor;
import de.flapdoodle.embed.process.store.IArtifactStore;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import ru.yandex.qatools.embed.postgresql.EmbeddedPostgres;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static ru.yandex.qatools.embed.postgresql.distribution.Version.Main.V9_6;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */

public abstract class PgTestBase {

  private static EmbeddedPostgres postgres;
  static PgConnectOptions options;

  @BeforeClass
  public static void before() throws Exception {
    options = startPg();
  }

  @AfterClass
  public static void after() throws Exception {
    stopPg();
  }

  public synchronized static PgConnectOptions startPg() throws Exception {
    if (postgres != null) {
      throw new IllegalStateException();
    }
    IRuntimeConfig config;
    String a = System.getProperty("target.dir");
    File targetDir;
    if (a != null && (targetDir = new File(a)).exists() && targetDir.isDirectory()) {
      config = EmbeddedPostgres.cachedRuntimeConfig(targetDir.toPath());
    } else {
      config = EmbeddedPostgres.defaultRuntimeConfig();
    }

    // SSL
    File sslKey = getResourceAsFile("tls/server.key");
    Files.setPosixFilePermissions(sslKey.toPath(), Collections.singleton(PosixFilePermission.OWNER_READ));
    File sslCrt = getResourceAsFile("tls/server.crt");

    postgres = new EmbeddedPostgres(V9_6);
    IRuntimeConfig sslConfig = new IRuntimeConfig() {
      @Override
      public ProcessOutput getProcessOutput() {
        return config.getProcessOutput();
      }
      @Override
      public ICommandLinePostProcessor getCommandLinePostProcessor() {
        ICommandLinePostProcessor commandLinePostProcessor = config.getCommandLinePostProcessor();
        return (distribution, args) -> {
          List<String> result = commandLinePostProcessor.process(distribution, args);
          if (result.get(0).endsWith("postgres")) {
            result = new ArrayList<>(result);
            result.add("--ssl=on");
            result.add("--ssl_cert_file=" + sslCrt.getAbsolutePath());
            result.add("--ssl_key_file=" + sslKey.getAbsolutePath());
          }
          return result;
        };
      }
      @Override
      public IArtifactStore getArtifactStore() {
        return config.getArtifactStore();
      }
      @Override
      public boolean isDaemonProcess() {
        return config.isDaemonProcess();
      }
    };
    PgTestBase.postgres.start(sslConfig,
      "localhost",
      8081,
      "postgres",
      "postgres",
      "postgres",
      Collections.emptyList());
    File setupFile = getResourceAsFile("create-postgres.sql");
    PgTestBase.postgres.getProcess().get().importFromFile(setupFile);
    PgConnectOptions options = new PgConnectOptions();
    options.setHost("localhost");
    options.setPort(8081);
    options.setUsername("postgres");
    options.setPassword("postgres");
    options.setDatabase("postgres");
    return options;
  }

  public synchronized static void stopPg() throws Exception {
    if (postgres != null) {
      try {
        postgres.stop();
      } finally {
        postgres = null;
      }
    }
  }

  private static File getResourceAsFile(String name) throws Exception {
    InputStream in = PgTestBase.class.getClassLoader().getResourceAsStream(name);
    Path path = Files.createTempFile("pg-client", ".tmp");
    Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
    File file = path.toFile();
    file.deleteOnExit();
    return file;
  }
}
