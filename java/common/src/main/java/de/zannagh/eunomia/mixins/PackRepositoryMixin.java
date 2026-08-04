package de.zannagh.eunomia.mixins;
//? if !fabric || < 26.1-0.snapshot.11 {
/*
public class PackRepositoryMixin {
}
*///?}
//? if fabric && >= 26.1-0.snapshot.11 {

import de.zannagh.eunomia.Eunomia;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.*;
import net.minecraft.world.flag.FeatureFlagSet;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Mixin(PackRepository.class)
public class PackRepositoryMixin {

    @Shadow @Final @Mutable
    private Set<RepositorySource> sources;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void armorHider$addModResources(RepositorySource[] sources, CallbackInfo ci) {
        if (FabricLoader.getInstance().isModLoaded("fabric-resource-loader-v0")) {
            // Fabric loaders handles pack respository resolution.
            return;
        }

        var newSources = new LinkedHashSet<>(this.sources);
        for (var provider : Eunomia.getPackRepositoryProviders()) {
            var modContainer = FabricLoader.getInstance().getModContainer(provider.getModId());
            if (modContainer.isEmpty()) return;

            List<Path> rootPaths = modContainer.get().getRootPaths().stream()
                    .filter(p -> Files.isDirectory(p.resolve(provider.getAssetDirectoryName())))
                    .toList();

            if (rootPaths.isEmpty()) {
                return;
            }

            newSources.add(consumer -> {
                for (int i = 0; i < rootPaths.size(); i++) {
                    Path rootPath = rootPaths.get(i);
                    String id = i == 0 ? provider.getModId() : provider.getModId() + "-" + i;
                    var locationInfo = new PackLocationInfo(
                            id,
                            Component.literal(provider.getModName()),
                            PackSource.BUILT_IN,
                            Optional.empty()
                    );
                    var resourcesSupplier = new PathPackResources.PathResourcesSupplier(rootPath);
                    var metadata = new Pack.Metadata(
                            Component.literal(provider.getModResourceName()),
                            PackCompatibility.COMPATIBLE,
                            FeatureFlagSet.of(),
                            List.of()
                    );
                    var selectionConfig = new PackSelectionConfig(true, Pack.Position.TOP, false);
                    var pack = new Pack(locationInfo, resourcesSupplier, metadata, selectionConfig);
                    consumer.accept(pack);
                }
            });
        }


        this.sources = newSources;
    }
}
//?}
