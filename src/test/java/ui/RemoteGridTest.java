package ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Demo UI Tests on Remote Selenium Grid")
public class RemoteGridTest {

    private WebDriver driver;

    @BeforeEach
    void setup() throws Exception {
        // 1. Пытаемся загрузить локальные секреты из файла .env.properties (который добавлен в .gitignore)
        Properties props = new Properties();
        Path envFile = Paths.get(".env.properties");
        
        if (Files.exists(envFile)) {
            try (InputStream in = Files.newInputStream(envFile)) {
                props.load(in);
            }
        }

        // 2. Читаем настройки: приоритет отдаем переменным среды (для CI/CD), 
        // затем локальному файлу .env.properties (для разработчика)
        String gridUser = System.getenv("GRID_USER") != null ? System.getenv("GRID_USER") : props.getProperty("GRID_USER");
        String gridPass = System.getenv("GRID_PASS") != null ? System.getenv("GRID_PASS") : props.getProperty("GRID_PASS");
        String gridDomain = System.getenv("GRID_DOMAIN") != null ? System.getenv("GRID_DOMAIN") : props.getProperty("GRID_DOMAIN");

        if (gridUser == null || gridPass == null || gridDomain == null) {
            throw new IllegalStateException(
                    "Credentials not found!\n" +
                    "Create a file named '.env.properties' in the project root with the following content:\n" +
                    "GRID_USER=admin\n" +
                    "GRID_PASS=superpass\n" +
                    "GRID_DOMAIN=selenium.my-domain.com\n" +
                    "(This file is ignored by Git and protects your secrets from terminal history)"
            );
        }

        // 3. Формируем URL для Basic Auth
        String gridUrl = String.format("https://%s:%s@%s/wd/hub", gridUser, gridPass, gridDomain);

        // 4. Настраиваем опции браузера (запускаем Chrome)
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");
        options.addArguments("--disable-infobars");
        options.addArguments("--disable-dev-shm-usage");

        // 5. Подключаемся к Grid
        this.driver = new RemoteWebDriver(URI.create(gridUrl).toURL(), options);
        this.driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
    }

    @AfterEach
    void teardown() {
        if (driver != null) {
            driver.quit(); // Обязательно закрываем сессию, чтобы освободить слот в Grid
        }
    }

    @Test
    @DisplayName("Проверка загрузки поисковика DuckDuckGo")
    void testDuckDuckGoSearch() throws InterruptedException {
        driver.get("https://duckduckgo.com/");
        
        String title = driver.getTitle();
        assertTrue(title.contains("DuckDuckGo"), "Заголовок страницы должен содержать DuckDuckGo");

        // Пауза 10 секунд для визуального наблюдения в веб-интерфейсе Selenium Grid UI
        System.out.println("⏳ Сессия Chrome активна! Ожидание 10 секунд для визуальной проверки в Selenium Grid UI...");
        Thread.sleep(10_000);
    }

    @Test
    @DisplayName("Проверка загрузки сайта Example.com")
    void testExampleCom() throws InterruptedException {
        driver.get("https://example.com/");
        
        String title = driver.getTitle();
        assertEquals("Example Domain", title, "Заголовок страницы должен быть Example Domain");

        // Пауза 10 секунд для визуального наблюдения в веб-интерфейсе Selenium Grid UI
        System.out.println("⏳ Сессия Chrome активна! Ожидание 10 секунд для визуальной проверки в Selenium Grid UI...");
        Thread.sleep(10_000);
    }
}