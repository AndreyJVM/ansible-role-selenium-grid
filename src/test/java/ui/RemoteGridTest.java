package ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.net.URL;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Demo UI Tests on Remote Selenium Grid")
public class RemoteGridTest {

    private WebDriver driver;

    @BeforeEach
    void setup() throws Exception {
        // 1. Безопасное получение учетных данных из переменных окружения ОС
        // Никогда не храним логины/пароли/IP в коде!
        String gridUser = System.getenv("GRID_USER");
        String gridPass = System.getenv("GRID_PASS");
        String gridDomain = System.getenv("GRID_DOMAIN");

        // Для локальной отладки (если переменные не заданы), просим их установить
        if (gridUser == null || gridPass == null || gridDomain == null) {
            throw new IllegalStateException(
                    "Environment variables GRID_USER, GRID_PASS, and GRID_DOMAIN must be set!\n" +
                    "Example: export GRID_USER=admin GRID_PASS=superpass GRID_DOMAIN=selenium.my-domain.com"
            );
        }

        // 2. Формируем URL для Basic Auth
        // Формат: https://user:pass@domain.com/wd/hub
        String gridUrl = String.format("https://%s:%s@%s/wd/hub", gridUser, gridPass, gridDomain);

        // 3. Настраиваем опции браузера (запускаем Chrome)
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");
        options.addArguments("--disable-infobars");
        options.addArguments("--disable-dev-shm-usage");

        // 4. Подключаемся к Grid
        this.driver = new RemoteWebDriver(new URL(gridUrl), options);
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
    void testDuckDuckGoSearch() {
        driver.get("https://duckduckgo.com/");
        
        String title = driver.getTitle();
        assertTrue(title.contains("DuckDuckGo"), "Заголовок страницы должен содержать DuckDuckGo");
    }

    @Test
    @DisplayName("Проверка загрузки сайта Example.com")
    void testExampleCom() {
        driver.get("https://example.com/");
        
        String title = driver.getTitle();
        assertEquals("Example Domain", title, "Заголовок страницы должен быть Example Domain");
    }
}
