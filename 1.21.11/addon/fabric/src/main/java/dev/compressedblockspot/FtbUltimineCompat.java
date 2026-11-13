package dev.compressedblockspot;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * FTB Ultimine（连锁破坏）收获兼容（附属压缩盆栽）：
 * 通过反射/动态代理挂接 FTB Ultimine 的 RegisterCropLikeEvent 注册 CropLikeHandler。
 * 无硬依赖：未安装 FTB Ultimine 时静默跳过。
 */
public final class FtbUltimineCompat {
    private static final String FTB_EVENT = "dev.ftb.mods.ftbultimine.api.crop.RegisterCropLikeEvent";
    private static final String FTB_HANDLER = "dev.ftb.mods.ftbultimine.api.crop.CropLikeHandler";
    private static final String FTB_DISPATCHER = "dev.ftb.mods.ftbultimine.api.crop.RegisterCropLikeEvent$Dispatcher";
    private static final String ARCH_EVENT = "dev.architectury.event.Event";

    private FtbUltimineCompat() {}

    public static void init() {
        try {
            Class<?> eventClass = Class.forName(FTB_EVENT);
            Class<?> handlerClass = Class.forName(FTB_HANDLER);
            Class<?> dispatcherClass = Class.forName(FTB_DISPATCHER);
            Class<?> archEventClass = Class.forName(ARCH_EVENT);

            Object registerEvent = eventClass.getField("REGISTER").get(null);

            Object listenerProxy = Proxy.newProxyInstance(
                FtbUltimineCompat.class.getClassLoader(),
                new Class<?>[] { eventClass },
                (proxy, method, args) -> {
                    if ("register".equals(method.getName()) && args != null && args.length == 1) {
                        Object dispatcher = args[0];
                        Object handlerProxy = createHandler(handlerClass);
                        dispatcherClass.getMethod("registerHandler", handlerClass).invoke(dispatcher, handlerProxy);
                    }
                    return null;
                }
            );

            archEventClass.getMethod("register", Object.class).invoke(registerEvent, listenerProxy);
        } catch (Throwable ignored) {
            // 未安装 FTB Ultimine 或 API 不匹配：静默跳过
        }
    }

    private static Object createHandler(Class<?> handlerClass) {
        return Proxy.newProxyInstance(
            FtbUltimineCompat.class.getClassLoader(),
            new Class<?>[] { handlerClass },
            new CropLikeInvocationHandler()
        );
    }

    private static class CropLikeInvocationHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("isApplicable".equals(name) && args != null && args.length == 3) {
                return isApplicable((Level) args[0], (BlockPos) args[1], (BlockState) args[2]);
            }
            if ("doHarvesting".equals(name) && args != null && args.length == 4) {
                @SuppressWarnings("unchecked")
                Consumer<ItemStack> collector = (Consumer<ItemStack>) args[3];
                return doHarvesting((Player) args[0], (BlockPos) args[1], (BlockState) args[2], collector);
            }
            if ("isEquivalent".equals(name) && args != null && args.length == 2) {
                return isEquivalent((BlockState) args[0], (BlockState) args[1]);
            }
            if ("toString".equals(name)) {
                return "CompressedPotsFtbCropLikeHandler";
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(name) && args != null && args.length == 1) {
                return proxy == args[0];
            }
            return null;
        }

        private boolean isApplicable(Level level, BlockPos pos, BlockState state) {
            if (level.getBlockEntity(pos) instanceof SpotPotBlockEntity pot) {
                return pot.grown();
            }
            return false;
        }

        private boolean doHarvesting(Player player, BlockPos pos, BlockState state, Consumer<ItemStack> collector) {
            Level level = player.level();
            if (level.getBlockEntity(pos) instanceof SpotPotBlockEntity pot) {
                if (!pot.grown()) {
                    return false;
                }
                List<ItemStack> drops = pot.harvestDrops(player);
                for (ItemStack drop : drops) {
                    if (drop.isEmpty()) {
                        continue;
                    }
                    if (collector != null) {
                        collector.accept(drop);
                    } else if (level instanceof ServerLevel server) {
                        Block.popResource(server, pos, drop);
                    }
                }
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 1.0F);
                return true;
            }
            return false;
        }

        private boolean isEquivalent(BlockState orig, BlockState other) {
            return isPot(orig) && isPot(other);
        }

        private static boolean isPot(BlockState state) {
            if (state == null) {
                return false;
            }
            if (state.getBlock() instanceof SpotPotBlock) {
                return true;
            }
            Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            String ns = id.getNamespace();
            return ("compressedblocks".equals(ns) || "compressedblockspot".equals(ns))
                && id.getPath().contains("pot");
        }
    }
}
