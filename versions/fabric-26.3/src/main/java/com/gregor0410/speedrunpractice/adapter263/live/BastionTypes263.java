package com.gregor0410.speedrunpractice.adapter263.live;

import com.gregor0410.speedrunpractice.adapter263.mixin.SinglePoolElementAccess263;
import com.mojang.datafixers.util.Either;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;

import java.util.Optional;

/**
 * Bastion-subtype reading shared by live structure lookups and seed
 * analysis: the start piece (always one of the four entries in vanilla
 * {@code template_pool/bastion/starts.json}) names the variant. Folder
 * names are NOT the community names: {@code treasure} and {@code bridge}
 * match, but stables live under {@code hoglin_stable} and housing under
 * {@code units} (verified against the 26.3 jar pools and headlessly on
 * seeds 12345 + the run-world nether).
 */
final class BastionTypes263 {
    private BastionTypes263() {
    }

    /**
     * Subtype of a bastion start, or null when the pieces are not
     * readable or name no known variant.
     */
    static String typeOf(StructureStart start) {
        if (start == null || !start.isValid() || start.getPieces().isEmpty()) {
            return null;
        }
        StructurePiece first = start.getPieces().get(0);
        if (!(first instanceof PoolElementStructurePiece)) {
            return null;
        }
        StructurePoolElement element = ((PoolElementStructurePiece) first).getElement();
        if (!(element instanceof SinglePoolElement)) {
            return null;
        }
        Either<Identifier, ?> template =
                ((SinglePoolElementAccess263) (Object) element).getTemplateReference();
        if (template == null) {
            return null;
        }
        Optional<Identifier> left = template.left();
        if (!left.isPresent()) {
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
