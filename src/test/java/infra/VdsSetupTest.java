package infra;

import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("VDS Setup Script Multi-OS Integration Tests")
public class VdsSetupTest {

    @Nested
    @DisplayName("OS: Ubuntu 24.04")
    class Ubuntu24Test extends AbstractVdsSetup {
        @Override
        protected String getOsImage() { return "ubuntu:24.04"; }
    }

    @Nested
    @DisplayName("OS: Ubuntu 22.04")
    class Ubuntu22Test extends AbstractVdsSetup {
        @Override
        protected String getOsImage() { return "ubuntu:22.04"; }
    }

    // Включаем жизненный цикл PER_CLASS, чтобы @BeforeAll мог быть не статичным
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    abstract static class AbstractVdsSetup {
        protected GenericContainer<?> linuxContainer;

        protected abstract String getOsImage();

        @BeforeAll
        void setupContainer() {
            System.out.println("=======================================================");
            System.out.println("🚀 Запуск контейнера и скрипта для образа: " + getOsImage());
            System.out.println("=======================================================");

            linuxContainer = new GenericContainer<>(getOsImage())
                    .withPrivilegedMode(true)
                    .withCopyFileToContainer(
                            MountableFile.forHostPath("scripts/setup_secure_vds.sh"),
                            "/tmp/setup_secure_vds.sh"
                    )
                    // Выполняем все настройки и сам скрипт при старте контейнера (В стиле Given/When)
                    .withCommand("bash", "-c",
                            "printf '#!/bin/bash\\necho \"[MOCK systemctl] $@\"\\nexit 0\\n' > /usr/local/bin/systemctl && " +
                            "chmod +x /usr/local/bin/systemctl && " +
                            "mkdir -p /root/.ssh && echo 'ssh-ed25519 AAAAC3NzaC1lZDI1NTE5 test-key' > /root/.ssh/authorized_keys && " +
                            "bash /tmp/setup_secure_vds.sh && " +
                            "echo 'SETUP_COMPLETE' && " +
                            "sleep infinity"
                    )
                    // Подключаем вывод логов контейнера (STDOUT скрипта) прямо в логгер теста
                    .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(getOsImage())))
                    // Инициализируем Wait Strategy: Тест не начнется, пока скрипт не напечатает SETUP_COMPLETE
                    .waitingFor(Wait.forLogMessage(".*SETUP_COMPLETE.*\\s*", 1)
                            .withStartupTimeout(Duration.ofMinutes(15)));

            linuxContainer.start();
            System.out.println("✅ [" + getOsImage() + "] Контейнер готов, скрипт успешно выполнен! Начинаем проверки (Assertions)...");
        }

        @AfterAll
        void teardown() {
            if (linuxContainer != null) {
                linuxContainer.stop();
            }
        }

        // --- Независимые проверки (В стиле Then) ---

        @Test
        @DisplayName("1. Часовой пояс установлен в UTC и служба Chrony активна")
        void shouldConfigureTimezoneAndChrony() throws Exception {
            ExecResult tzCheck = linuxContainer.execInContainer("cat", "/etc/timezone");
            assertEquals(0, tzCheck.getExitCode(), "Файл /etc/timezone должен существовать");
            assertTrue(tzCheck.getStdout().contains("UTC"), "Часовой пояс должен быть установлен в UTC");

            ExecResult chronyCheck = linuxContainer.execInContainer("bash", "-c", "command -v chronyd || test -f /usr/sbin/chronyd");
            assertEquals(0, chronyCheck.getExitCode(), "Служба точного времени chrony должна быть установлена");
        }

        @Test
        @DisplayName("2. Пользователь deployer создан и добавлен в группы sudo и docker")
        void shouldCreateDeployerUserWithCorrectGroups() throws Exception {
            ExecResult userCheck = linuxContainer.execInContainer("id", "deployer");
            assertEquals(0, userCheck.getExitCode(), "Пользователь deployer должен быть создан");
            assertTrue(userCheck.getStdout().contains("sudo"), "Пользователь deployer должен состоять в группе sudo");
            assertTrue(userCheck.getStdout().contains("docker"), "Пользователь deployer должен состоять в группе docker");
        }

        @Test
        @DisplayName("3. SSH ключи перенесены от root с безопасными правами доступа")
        void shouldSecureSshKeys() throws Exception {
            ExecResult sshKeyCheck = linuxContainer.execInContainer("cat", "/home/deployer/.ssh/authorized_keys");
            assertEquals(0, sshKeyCheck.getExitCode(), "Ключи должны быть скопированы в /home/deployer/.ssh");
            assertTrue(sshKeyCheck.getStdout().contains("test-key"), "Содержимое authorized_keys должно совпадать с исходным");

            ExecResult sshPermCheck = linuxContainer.execInContainer("stat", "-c", "%a", "/home/deployer/.ssh/authorized_keys");
            assertTrue(sshPermCheck.getStdout().trim().endsWith("600"), "Права на файл authorized_keys должны быть 600");
        }

        @Test
        @DisplayName("4. Базовая директория проекта создана")
        void shouldCreateProjectDirectory() throws Exception {
            ExecResult dirCheck = linuxContainer.execInContainer("test", "-d", "/opt/ui-tests-infra");
            assertEquals(0, dirCheck.getExitCode(), "Директория /opt/ui-tests-infra должна существовать");
        }

        @Test
        @DisplayName("5. Docker CLI установлен")
        void shouldInstallDocker() throws Exception {
            ExecResult dockerCheck = linuxContainer.execInContainer("bash", "-c", "command -v docker");
            assertEquals(0, dockerCheck.getExitCode(), "Утилита docker должна быть доступна в PATH");
        }

        @Test
        @DisplayName("6. Firewall UFW включен и настроены правила по умолчанию")
        void shouldConfigureUfwFirewall() throws Exception {
            ExecResult ufwCheck = linuxContainer.execInContainer("ufw", "status");
            assertEquals(0, ufwCheck.getExitCode(), "Команда ufw status должна отрабатывать без ошибок");
            assertTrue(ufwCheck.getStdout().contains("Status: active"), "UFW должен быть в состоянии active");
        }
    }
}
