package com.example.soundattract.mixin;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;

class HumanoidArmorLayerMixinTest {
    @Test
    void preservesNamespaceAndTextureRules() {
        assertEquals(Identifier.parse("immersiveores:textures/models/armor/enderium_layer_1.png"),
            invokeBuildArmorTexture("immersiveores:enderium", EquipmentSlot.CHEST, null));
        assertEquals(Identifier.parse("immersiveores:textures/models/armor/enderium_layer_2_overlay.png"),
            invokeBuildArmorTexture("immersiveores:enderium", EquipmentSlot.LEGS, "overlay"));
    }

    @Test
    void preservesDefaultNamespace() {
        assertEquals(Identifier.parse("minecraft:textures/models/armor/leather_layer_1.png"),
            invokeBuildArmorTexture("leather", EquipmentSlot.HEAD, null));
    }

    @Test
    void rejectsMalformedMaterialNames() {
        assertNull(invokeBuildArmorTexture("bad:name:extra", EquipmentSlot.FEET, null));
    }

    @Test
    void ordinaryMaterialHelperIsPrivateStatic() throws NoSuchMethodException {
        Method method = helperMethod();
        org.junit.jupiter.api.Assertions.assertTrue(Modifier.isPrivate(method.getModifiers()));
        org.junit.jupiter.api.Assertions.assertTrue(Modifier.isStatic(method.getModifiers()));
    }

    @Test
    void hasOneOrderedArmorTailAndFinallyReset() throws Exception {
        String source = Files.readString(Path.of("src/client/java/com/example/soundattract/mixin/HumanoidArmorLayerMixin.java"));
        assertEquals(1, source.split("@Inject\\(method = \\\"renderArmorPiece\\\", at = @At\\(\\\"TAIL\\\"\\)\\)", -1).length - 1);
        assertTrue(source.contains("ArmorValidationTracker.markSlotValidated(entity, slot)"));
        assertTrue(source.contains("collector.submitModel(model, state"));
    }

    private static Identifier invokeBuildArmorTexture(String materialName, EquipmentSlot slot, String suffix) {
        try {
            return (Identifier) helperMethod().invoke(null, materialName, slot, suffix);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Method helperMethod() throws NoSuchMethodException {
        Method method = HumanoidArmorLayerMixin.class.getDeclaredMethod(
            "soundattract$buildArmorTexture", String.class, EquipmentSlot.class, String.class);
        method.setAccessible(true);
        return method;
    }
}

