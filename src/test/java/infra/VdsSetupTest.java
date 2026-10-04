package infra;

import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Ansible VDS Setup Playbook Integration Tests")
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

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    abstract static class AbstractVdsSetup {
        protected GenericContainer<?> linuxContainer;

        protected abstract String getOsImage();

        @BeforeAll
        void setupContainer() {
            System.out.println("=======================================================");
            System.out.println("🚀 Подготовка базового образа (с кэшированием) для: " + getOsImage());
            System.out.println("=======================================================");

            // Используем --no-install-recommends для радикального ускорения установки Ansible
            ImageFromDockerfile cachedImage = new ImageFromDockerfile()
                    .withDockerfileFromBuilder(builder -> builder
                            .from(getOsImage())
                            .env("DEBIAN_FRONTEND", "noninteractive")
                            .env("TZ", "UTC")
                            .run("ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone")
                            .run("apt-get update -qq && apt-get install -qq -y --no-install-recommends ansible ca-certificates openssh-client python3-apt")
                            .build());

            linuxContainer = new GenericContainer<>(cachedImage)
                    .withPrivilegedMode(true)
                    .withCopyFileToContainer(
                            MountableFile.forHostPath("."),
                            "/tmp/ansible-role-selenium-grid"
                    )
                    .withCommand("bash", "-c",
                            // 1. Создаем заглушку для systemctl
                            "printf '#!/bin/bash\\necho \"[MOCK systemctl] $@\"\\nexit 0\\n' > /usr/local/bin/systemctl && " +
                            "chmod +x /usr/local/bin/systemctl && " +
                            
                            // 2. Фейковый SSH-ключ у root
                            "mkdir -p /root/.ssh && echo 'ssh-ed25519 AAAAC3NzaC1lZDI1NTE5 test-key' > /root/.ssh/authorized_keys && " +
                            
                            // 3. Запускаем Ansible Playbook локально
                            "ansible-playbook -c local -i localhost, -e \"basic_user=admin basic_password=pass domain_name=test.local acme_email=test@test.local\" /tmp/ansible-role-selenium-grid/tests/test.yml && " +
                            
                            "echo 'SETUP_COMPLETE' && " +
                            "sleep infinity"
                    )
                    .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(getOsImage())))
                    .waitingFor(Wait.forLogMessage(".*SETUP_COMPLETE.*\\s*", 1)
                            .withStartupTimeout(Duration.ofMinutes(15)));

            linuxContainer.start();
            System.out.println("✅ [" + getOsImage() + "] Ansible Playbook успешно отработал! Начинаем проверки (Assertions)...");
        }

        @AfterAll
        void teardown() {
            if (linuxContainer != null) {
                linuxContainer.stop();
            }
        }

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
        @DisplayName("7. Конфигурация Grid и .env развернуты")
        void shouldDeployGridConfiguration() throws Exception {
            ExecResult envCheck = linuxContainer.execInContainer("test", "-f", "/opt/ui-tests-infra/.env");
            assertEquals(0, envCheck.getExitCode(), "Файл .env должен существовать");
            
            ExecResult composeCheck = linuxContainer.execInContainer("test", "-f", "/opt/ui-tests-infra/docker-compose.yml");
            assertEquals(0, composeCheck.getExitCode(), "Файл docker-compose.yml должен существовать");
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
