import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Properties;

/**
 * Renders the affected Spanish e-mail messages exactly the way Keycloak does:
 * MessageFormat over the bundle value, with the pre-formatted expiration period
 * (produced by LinkExpirationFormatterMethod) passed as the {3}/{4} parameter.
 *
 * Run: java RenderCheck.java
 */
public class RenderCheck {

    public static void main(String[] args) throws Exception {
        for (String file : new String[]{"messages_es.bug.properties", "messages_es.fixed.properties"}) {
            Properties p = new Properties();
            try (var r = Files.newBufferedReader(Path.of(file), StandardCharsets.UTF_8)) {
                p.load(r);
            }
            System.out.println("== " + file + " ==");
            for (long seconds : new long[]{300, 60, 43200}) {
                String period = linkExpirationFormatter(seconds, p);
                render(p, "passwordResetBody", new Object[]{"https://kc/link", seconds / 60, "demo", period});
                render(p, "executeActionsBody", new Object[]{"https://kc/link", seconds / 60, "demo", "Actualizar contraseña", period});
            }
            System.out.println();
        }
    }

    static void render(Properties p, String key, Object[] params) {
        String raw = p.getProperty(key).replace("\\n", "\n");
        String out = new MessageFormat(raw, Locale.forLanguageTag("es")).format(params);
        for (String line : out.split("\n"))
            if (line.contains("expirará") || line.contains("caducará"))
                System.out.printf("  %-20s %s%n", key, line.trim());
    }

    // Verbatim logic of org.keycloak.theme.beans.LinkExpirationFormatterMethod#format
    // (github.com/keycloak/keycloak, Apache License 2.0)
    static String linkExpirationFormatter(long valueInSeconds, Properties messages) {
        String unitKey = "seconds";
        long value = valueInSeconds;
        if (value > 0 && value % 60 == 0) {
            unitKey = "minutes";
            value = value / 60;
            if (value % 60 == 0) {
                unitKey = "hours";
                value = value / 60;
                if (value % 24 == 0) {
                    unitKey = "days";
                    value = value / 24;
                }
            }
        }
        return value + " " + MessageFormat.format(messages.getProperty("linkExpirationFormatter.timePeriodUnit." + unitKey), value);
    }
}
