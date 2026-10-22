package com.example.soundattract.mixin;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomNpcsRenderingMixinTest {
    @Test
    void customNpcTargetsAndGatesAreExact() throws Exception {
        String entity = Files.readString(Path.of("src/main/java/com/example/soundattract/mixin/CustomNpcsEntityNPCInterfaceMixin.java"));
        assertTrue(entity.contains("isInvisible()Z"));
        assertTrue(entity.contains("cancellable = true"));
        assertTrue(entity.contains("MobEffects.INVISIBILITY"));
        String renderer = Files.readString(Path.of("src/main/java/com/example/soundattract/mixin/CustomNpcsRenderCustomNpcMixin.java"));
        assertTrue(renderer.contains("render(Lnoppes/npcs/entity/EntityNPCInterface;"));
        assertTrue(renderer.contains("npclayers"));
        assertEquals(1, renderer.split("new GenericMobCamoLayer", -1).length - 1);
        String item = Files.readString(Path.of("src/main/java/com/example/soundattract/mixin/CustomNpcsLayerBackItemMixin.java"));
        assertTrue(item.contains("cancellable = true"));
        assertTrue(item.contains("shouldHideWornGearWhileInvisible"));
        String json = Files.readString(Path.of("src/main/resources/soundattract.mixins.json"));
        assertTrue(json.indexOf("CustomNpcsRenderCustomNpcMixin") > json.indexOf("\"client\""));
        assertTrue(json.indexOf("CustomNpcsLayerBackItemMixin") > json.indexOf("\"client\""));
    }
}
