The genetic allele order, encoding, recombination/linkage and core attribute formulas derive from **Realistic Horse Genetics**, by **sekelsta and contributors**, licensed LGPL-3.0-or-later.

Source: https://github.com/sekelsta/horse-colors/tree/08a9487cd4758de16ef6ed75c1157221c468cb79 (1.20.1 branch).

Reviewed files: `entity/genetics/Genome.java`, `entity/genetics/EquineGenome.java`, `entity/AbstractHorseGenetic.java`, `util/Util.java` under `src/main/java/horse_colors`.

The 157 gene names and their order were checked against `realistic_horse_genetics-1.20.1-13.5.jar`. No RHG renderer, texture, entity registration or potion is bundled. This addon provides all corresponding modified source code and build scripts under LGPL-3.0-or-later; see LICENSE and COPYING-GPL-3.0.txt. Texture artwork from RHG is not distributed.

DragN’s Livestock Overhaul 1.20.1-3.9.4 remains an unmodified, separately installed dependency under its original license. No decompiled DLO source is distributed. Native DLO models, appearances, breed inheritance, gender, training and riding continue to run in that mod.

Changes from the original RHG behavior: native DLO coats/breeds replace dynamically composed RHG coats; pedigree and complete original genomes remain saved, while only the speed, jump, athletic and immune/health loci affect new stat calculations. Genetic disease, lethal-color and separate RHG body-size rendering are not applied. Existing DLO stats are calibrated when a founder genome is first assigned; later DLO training changes attributes without rewriting hereditary genes. Gestation/cooldown use the server overworld game clock, including time spent in unloaded chunks.
