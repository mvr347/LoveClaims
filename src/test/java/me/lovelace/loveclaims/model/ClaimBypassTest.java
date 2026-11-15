package me.lovelace.loveclaims.model;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.Set;
import me.lovelace.loveclaims.util.ClaimBypass;
import org.bukkit.permissions.Permissible;
import org.junit.jupiter.api.Test;

class ClaimBypassTest {
  private boolean bypass(String... permissions) {
    var allowed = Set.of(permissions);
    var player =
        (Permissible)
            Proxy.newProxyInstance(
                Permissible.class.getClassLoader(),
                new Class<?>[] {Permissible.class},
                (proxy, method, args) -> {
                  if (method.getName().equals("hasPermission")) return allowed.contains(args[0]);
                  throw new UnsupportedOperationException(method.getName());
                });
    return ClaimBypass.allowed(player);
  }

  @Test
  void managingClaimsDoesNotImplicitlyAllowBreakingEveryClaim() {
    assertFalse(bypass());
    assertFalse(bypass("loveclaims.admin", "loveclaims.moderator"));
    assertTrue(bypass("loveclaims.bypass"));
  }

  @Test
  void operatorsDoNotReceiveTheBypassNodeByDefault() throws Exception {
    var yaml = new org.bukkit.configuration.file.YamlConfiguration();
    try (var in = getClass().getClassLoader().getResourceAsStream("plugin.yml")) {
      yaml.load(
          new java.io.InputStreamReader(
              java.util.Objects.requireNonNull(in), java.nio.charset.StandardCharsets.UTF_8));
    }
    assertEquals(Boolean.FALSE, yaml.get("permissions.loveclaims.bypass.default"));
    assertEquals("op", yaml.getString("permissions.loveclaims.admin.default"));
  }
}
