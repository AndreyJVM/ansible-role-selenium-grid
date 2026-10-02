package infra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("VDS Setup Script Multi-OS Integration Tests")
public class VdsSetupTest {

    @ParameterizedTest(name = "OS: {0}")
    @ValueSource(strings = {
        "ubuntu:24.04",
        "ubuntu:22.04"
        // "debian:12" // Раскомментируйте для теста под базу Astra Linux 1.8
    })
    @DisplayName("Should configure secure VDS environment across supported Linux distributions")
    public void testVdsSetupScript(String dockerImage) throws Exception {
        String scriptPath = "scripts/setup_secure_vds.sh";
        assertTrue(Paths.get(scriptPath).toFile().exists(), 
                "Скрипт не найден по пути: " + scriptPath);

        System.out.println("\n=======================================================");
        System.out.println("Запуск интеграционного теста на образе: " + dockerImage);
        System.out.println("=======================================================");
        
        try (GenericContainer<?> linuxContainer = new GenericContainer<>(dockerImage)
                .withCommand("sleep", "infinity")
                .withPrivilegedMode(true)) {
            
            linuxContainer.start();
            System.out.println("[" + dockerImage + "] Контейнер запущен. Подготавливаем тестовое окружение...");

            // 1. Создаем мок для systemctl
            linuxContainer.execInContainer("bash", "-c", 
                "printf '#!/bin/bash\\necho \"[MOCK systemctl] $@\"\\nexit 0\\n' > /usr/local/bin/systemctl && chmod +x /usr/local/bin/systemctl");

            // 2. Создаем тестовый SSH ключ
            linuxContainer.execInContainer("bash", "-c", 
                "mkdir -p /root/.ssh && echo 'ssh-ed25519 AAAAC3NzaC1lZDI1NTE5 test-key' > /root/.ssh/authorized_keys");

            // 3. Копируем и выполняем скрипт
            linuxContainer.copyFileToContainer(MountableFile.forHostPath(scriptPath), "/tmp/setup_secure_vds.sh");
            System.out.println("[" + dockerImage + "] Выполняем setup_secure_vds.sh...");
            
            ExecResult scriptResult = linuxContainer.execInContainer("bash", "/tmp/setup_secure_vds.sh");
            
            if (scriptResult.getExitCode() != 0) {
                System.out.println("========== SCRIPT STDOUT ==========\n" + scriptResult.getStdout());
                System.err.println("========== SCRIPT STDERR ==========\n" + scriptResult.getStderr());
            }

            assertEquals(0, scriptResult.getExitCode(), 
                "Скрипт завершился с кодом ошибки: " + scriptResult.getExitCode() + "\nStderr: " + scriptResult.getStderr());

            System.out.println("[" + dockerImage + "] Скрипт отработал. Запуск Assertion-проверок...");

            // --- БЛОК ASSERTIONS (assertAll позволяет выполнить все проверки даже если первая упадет) ---
            assertAll(
                "Проверки инфраструктуры VDS",

                // 1. Проверки времени и часового пояса
                () -> {
                    ExecResult tzCheck = linuxContainer.execInContainer("cat", "/etc/timezone");
                    assertEquals(0, tzCheck.getExitCode(), "Файл /etc/timezone должен существовать");
                    assertTrue(tzCheck.getStdout().contains("UTC"), "Часовой пояс должен быть UTC");
                },
                () -> {
                    ExecResult chronyCheck = linuxContainer.execInContainer("bash", "-c", "command -v chronyd || test -f /usr/sbin/chronyd");
                    assertEquals(0, chronyCheck.getExitCode(), "Служба chrony должна быть установлена");
                },

                // 2. Проверки пользователя deployer
                () -> {
                    ExecResult userCheck = linuxContainer.execInContainer("id", "deployer");
                    assertEquals(0, userCheck.getExitCode(), "Пользователь deployer должен быть создан");
                    assertTrue(userCheck.getStdout().contains("sudo"), "Пользователь deployer должен состоять в sudo");
                    assertTrue(userCheck.getStdout().contains("docker"), "Пользователь deployer должен состоять в docker");
                },

                // 3. Проверки SSH ключей
                () -> {
                    ExecResult sshKeyCheck = linuxContainer.execInContainer("cat", "/home/deployer/.ssh/authorized_keys");
                    assertEquals(0, sshKeyCheck.getExitCode(), "SSH ключи должны быть скопированы");
                    assertTrue(sshKeyCheck.getStdout().contains("test-key"), "Содержимое authorized_keys должно совпадать");
                    
                    // Проверка строгих прав доступа (700 для .ssh, 600 для ключа)
                    ExecResult sshPermCheck = linuxContainer.execInContainer("stat", "-c", "%a", "/home/deployer/.ssh/authorized_keys");
                    assertTrue(sshPermCheck.getStdout().trim().endsWith("600"), "Права на ключ должны быть 600");
                },

                // 4. Проверки директории проекта
                () -> {
                    ExecResult dirCheck = linuxContainer.execInContainer("test", "-d", "/opt/ui-tests-infra");
                    assertEquals(0, dirCheck.getExitCode(), "Директория /opt/ui-tests-infra должна существовать");
                },

                // 5. Проверки Docker Engine
                () -> {
                    ExecResult dockerCheck = linuxContainer.execInContainer("bash", "-c", "command -v docker");
                    assertEquals(0, dockerCheck.getExitCode(), "Утилита docker должна быть установлена");
                },

                // 6. Проверки UFW
                () -> {
                    ExecResult ufwCheck = linuxContainer.execInContainer("ufw", "status");
                    assertEquals(0, ufwCheck.getExitCode(), "UFW должен отдавать статус");
                    assertTrue(ufwCheck.getStdout().contains("Status: active"), "UFW должен быть активен");
                }
            );

            System.out.println("✅ [" + dockerImage + "] Все проверки успешно пройдены!");
        }
    }
}
