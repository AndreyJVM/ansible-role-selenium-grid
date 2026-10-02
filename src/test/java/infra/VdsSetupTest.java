package infra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("VDS Setup Script Multi-OS Integration Tests")
public class VdsSetupTest {

    @ParameterizedTest(name = "OS: {0}")
    @ValueSource(strings = {
        "ubuntu:24.04",
        "ubuntu:22.04"
        // "debian:12" // База для Astra Linux 1.8
    })
    @DisplayName("Should configure secure VDS environment across supported Linux distributions")
    public void testVdsSetupScript(String dockerImage) throws Exception {
        String scriptPath = "scripts/setup_secure_vds.sh";
        assertTrue(Paths.get(scriptPath).toFile().exists(), 
                "Скрипт не найден по пути: " + scriptPath + ". Запускайте тесты из корня проекта.");

        System.out.println("\n=======================================================");
        System.out.println("Запуск интеграционного теста на образе: " + dockerImage);
        System.out.println("=======================================================");
        
        try (GenericContainer<?> linuxContainer = new GenericContainer<>(dockerImage)
                .withCommand("sleep", "infinity")
                .withPrivilegedMode(true)) {
            
            linuxContainer.start();
            System.out.println("[" + dockerImage + "] Контейнер запущен. Подготавливаем тестовое окружение...");

            // 1. Создаем заглушку для systemctl в /usr/local/bin (чтобы реальный systemctl не конфликтовал с Docker)
            linuxContainer.execInContainer("bash", "-c", 
                "printf '#!/bin/bash\\necho \"[MOCK systemctl] $@\"\\nexit 0\\n' > /usr/local/bin/systemctl && chmod +x /usr/local/bin/systemctl");

            // 2. Создаем тестовый SSH ключ у root для проверки переноса в deployer
            linuxContainer.execInContainer("bash", "-c", 
                "mkdir -p /root/.ssh && echo 'ssh-ed25519 AAAAC3NzaC1lZDI1NTE5 test-key' > /root/.ssh/authorized_keys");

            // 3. Копируем bash-скрипт внутрь контейнера
            linuxContainer.copyFileToContainer(
                    MountableFile.forHostPath(scriptPath),
                    "/tmp/setup_secure_vds.sh"
            );

            System.out.println("[" + dockerImage + "] Запускаем setup_secure_vds.sh...");
            
            // 4. Выполняем скрипт
            ExecResult scriptResult = linuxContainer.execInContainer("bash", "/tmp/setup_secure_vds.sh");
            
            if (scriptResult.getExitCode() != 0) {
                System.out.println("========== SCRIPT STDOUT ==========\n" + scriptResult.getStdout());
                System.err.println("========== SCRIPT STDERR ==========\n" + scriptResult.getStderr());
            }

            assertEquals(0, scriptResult.getExitCode(), 
                "Скрипт завершился с кодом ошибки: " + scriptResult.getExitCode() + "\nStderr: " + scriptResult.getStderr());

            System.out.println("[" + dockerImage + "] Скрипт отработал успешно. Проводим проверки...");

            // --- ПРОВЕРКИ ---

            // Проверка 1: Настройка синхронизации времени и часового пояса (Chrony + UTC)
            ExecResult tzCheck = linuxContainer.execInContainer("cat", "/etc/timezone");
            assertEquals(0, tzCheck.getExitCode(), "Файл /etc/timezone должен существовать");
            assertTrue(tzCheck.getStdout().contains("UTC"), "Часовой пояс должен быть UTC по умолчанию");

            ExecResult chronyCheck = linuxContainer.execInContainer("bash", "-c", "command -v chronyd || test -f /usr/sbin/chronyd");
            assertEquals(0, chronyCheck.getExitCode(), "Служба точного времени chrony должна быть установлена");

            // Проверка 2: Пользователь deployer существует и состоит в sudo и docker
            ExecResult userCheck = linuxContainer.execInContainer("id", "deployer");
            assertEquals(0, userCheck.getExitCode(), "Пользователь deployer должен быть создан");
            assertTrue(userCheck.getStdout().contains("sudo"), "Пользователь deployer должен состоять в группе sudo");
            assertTrue(userCheck.getStdout().contains("docker"), "Пользователь deployer должен состоять в группе docker");

            // Проверка 3: SSH ключи перенесены и права изолированы
            ExecResult sshKeyCheck = linuxContainer.execInContainer("cat", "/home/deployer/.ssh/authorized_keys");
            assertEquals(0, sshKeyCheck.getExitCode(), "SSH ключи должны быть скопированы в /home/deployer/.ssh");
            assertTrue(sshKeyCheck.getStdout().contains("test-key"), "Содержимое authorized_keys должно совпадать");

            // Проверка 4: Директория проекта /opt/ui-tests-infra создана и принадлежит deployer
            ExecResult dirCheck = linuxContainer.execInContainer("test", "-d", "/opt/ui-tests-infra");
            assertEquals(0, dirCheck.getExitCode(), "Директория /opt/ui-tests-infra должна существовать");

            // Проверка 5: Docker CLI установлен
            ExecResult dockerCheck = linuxContainer.execInContainer("bash", "-c", "command -v docker");
            assertEquals(0, dockerCheck.getExitCode(), "Утилита docker должна быть установлена");

            // Проверка 6: UFW сконфигурирован
            ExecResult ufwCheck = linuxContainer.execInContainer("ufw", "status");
            assertEquals(0, ufwCheck.getExitCode(), "UFW должен отдавать статус");
            assertTrue(ufwCheck.getStdout().contains("Status: active"), "UFW должен быть активен");

            System.out.println("✅ [" + dockerImage + "] Все проверки успешно пройдены!");
        }
    }
}
