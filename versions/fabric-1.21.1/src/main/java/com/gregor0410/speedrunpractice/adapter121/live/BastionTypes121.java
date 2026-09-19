package com.gregor0410.speedrunpractice.adapter121.live;

import com.gregor0410.speedrunpractice.adapter121.mixin.SinglePoolElementAccess121;
import com.mojang.datafixers.util.Either;
import net.minecraft.structure.PoolStructurePiece;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureStart;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.pool.SinglePoolElement;
import net.minecraft.structure.pool.StructurePoolElement;
import net.minecraft.util.Identifier;

import java.util.Optional;

/**
 * Bastion-subtype reading shared by live structure lookups and seed
 * analysis: the start piece (always one of the four entries in vanilla
 * {@code template_pool/bastion/starts}) names the variant. Folder names are
 * NOT the community names: {@code treasure} and {@code bridge} match, but
 * stables live under {@code hoglin_stable} and housing under {@code units}.
 */
final class BastionTypes121 {
    private BastionTypes121() {
    }

    /**
     * Subtype of a bastion start, or null when the pieces are not
     * readable or name no known variant.
     */
    static String typeOf(StructureStart start) {
        if (start == null || !start.hasChildren() || start.getChildren().isEmpty()) {
            return null;
        }
        StructurePiece first = start.getChildren().get(0);
        if (!(first instanceof PoolStructurePiece)) {
            return null;
        }
        StructurePoolElement element = ((PoolStructurePiece) first).getPoolElement();
        if (!(element instanceof SinglePoolElement)) {
            return null;
        }
        Either<Identifier, StructureTemplate> template =
                ((SinglePoolElementAccess121) (Object) element).getLocation();
        if (template == null) {
            return null;
        }
        Optional<Identifier> left = template.left();
        if (left == null || !left.isPresent()) {
            return null;
        }
        String path = left.get().getPath();
        if (!path.startsWith("bastion/")) {
            return null;
        }
        String[] parts = path.split("/");
        if (parts.length < 2) {
            return null;
        }
        String folder = parts[1];
        if ("treasure".equals(folder) || "bridge".equals(folder)) {
            return folder;
        }
        if ("hoglin_stable".equals(folder)) {
            return "stables";
        }
        if ("units".equals(folder)) {
            return "housing";
        }
        return null;
    }
}
