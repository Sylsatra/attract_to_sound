package com.example.soundattract.integration.customnpcs;

import com.example.soundattract.SoundAttractMod;
import com.example.soundattract.config.SoundAttractConfig;
import com.example.soundattract.event.StealthDetectionEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Consumer;

public final class CustomNpcsStealthTargetBridge {
    private static boolean registered;

    private CustomNpcsStealthTargetBridge() {
    }

    public static void registerIfPresent() {
        if (registered
                || !ModList.get().isLoaded("customnpcs")
                || SoundAttractConfig.COMMON == null
                || !SoundAttractConfig.COMMON.enableCustomNpcsIntegration.get()) {
            return;
        }

        try {
            Class<?> npcApiClass = Class.forName("noppes.npcs.api.NpcAPI");
            Object npcApi = npcApiClass.getMethod("Instance").invoke(null);
            if (npcApi == null) {
                return;
            }

            Object eventBus = npcApiClass.getMethod("events").invoke(npcApi);
            Class<?> targetEventClass = Class.forName("noppes.npcs.api.event.NpcEvent$TargetEvent");
            if (!(eventBus instanceof IEventBus customNpcsBus) || !Event.class.isAssignableFrom(targetEventClass)) {
                return;
            }

            registerTargetListener(customNpcsBus, targetEventClass.asSubclass(Event.class));
            registered = true;
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.info("[CustomNPCs] Registered stealth target event bridge.");
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            SoundAttractMod.LOGGER.error("[CustomNPCs] Failed to register stealth target event bridge.", e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerTargetListener(IEventBus eventBus, Class<? extends Event> targetEventClass) {
        eventBus.addListener(EventPriority.NORMAL, false, (Class) targetEventClass, (Consumer<Event>) CustomNpcsStealthTargetBridge::onTargetEvent);
    }

    private static void onTargetEvent(Event event) {
        if (event == null
                || SoundAttractConfig.COMMON == null
                || !SoundAttractConfig.COMMON.enableCustomNpcsIntegration.get()
                || !SoundAttractConfig.COMMON.enableStealthMechanics.get()) {
            return;
        }

        try {
            Object npcWrapper = getField(event, "npc");
            Object targetWrapper = getField(event, "entity");
            Object npcEntity = getMcEntity(npcWrapper);
            Object targetEntity = getMcEntity(targetWrapper);

            if (!(npcEntity instanceof Mob npc) || !(targetEntity instanceof LivingEntity target)) {
                return;
            }

            if (!StealthDetectionEvents.canMobDetectLivingEntity(npc, target)) {
                if (event instanceof ICancellableEvent cancellableEvent) {
                    cancellableEvent.setCanceled(true);
                } else if (!cancelReflectively(event)) {
                    npc.setTarget(null);
                    npc.getNavigation().stop();
                }
                if (SoundAttractConfig.COMMON.debugLogging.get()) {
                    SoundAttractMod.LOGGER.info(
                            "[CustomNPCs] Canceled target event: {} cannot detect {} through stealth.",
                            npc.getName().getString(),
                            target.getName().getString());
                }
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            if (SoundAttractConfig.COMMON.debugLogging.get()) {
                SoundAttractMod.LOGGER.warn("[CustomNPCs] Failed to evaluate stealth target event.", e);
            }
        }
    }

    private static boolean cancelReflectively(Event event) throws ReflectiveOperationException {
        try {
            Method method = event.getClass().getMethod("setCanceled", boolean.class);
            method.invoke(event, true);
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    private static Object getField(Object instance, String fieldName) throws ReflectiveOperationException {
        Class<?> type = instance.getClass();
        while (type != null) {
            try {
                Field field = type.getField(fieldName);
                return field.get(instance);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static Object getMcEntity(Object wrapper) throws ReflectiveOperationException {
        if (wrapper == null) {
            return null;
        }
        Method method = wrapper.getClass().getMethod("getMCEntity");
        return method.invoke(wrapper);
    }
}
