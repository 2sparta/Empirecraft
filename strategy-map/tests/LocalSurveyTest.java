package ua.empirecraft.townymap;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.*;

class LocalSurveyTest {
    private static final String WORLD = "minecraft:overworld";
    private static final ClaimKnowledge.Chunk CENTER = new ClaimKnowledge.Chunk(WORLD, -2, -4);

    @Test void surveysNineByNineNearestFirstWithBoundedWork() {
        var survey = new LocalSurvey();
        var sampled = new LinkedHashSet<ClaimKnowledge.Chunk>();
        for (int tick=0; tick<10; tick++) {
            int before = sampled.size();
            survey.visit(CENTER, 1, tick, chunk -> assertNew(sampled, chunk));
            assertTrue(sampled.size() - before <= LocalSurvey.PER_TICK);
        }
        assertEquals(CENTER,sampled.iterator().next());
        assertEquals(81,sampled.size());
        assertTrue(sampled.contains(new ClaimKnowledge.Chunk(WORLD, -6, 0)));
        assertFalse(sampled.contains(new ClaimKnowledge.Chunk(WORLD, -7, -4)));
    }

    @Test void stationarySurveyPicksUpChunksLoadedAfterItsFirstSweep() {
        var survey = new LocalSurvey();
        var sampled = new HashSet<ClaimKnowledge.Chunk>();
        var delayed = new ClaimKnowledge.Chunk(WORLD, -1, -4);
        for (int tick=0; tick<39; tick++) survey.visit(CENTER, 1, tick, chunk ->
            !delayed.equals(chunk) && assertNew(sampled, chunk));
        assertEquals(80,sampled.size());
        survey.visit(CENTER, 1, 40, chunk -> assertNew(sampled,chunk));
        assertTrue(sampled.contains(delayed));
        assertEquals(81,sampled.size());
    }

    @Test void revisitsOnPoliticalRefreshAndWhenChangingDimensions() {
        var survey = new LocalSurvey();
        var visits = new ArrayList<ClaimKnowledge.Chunk>();
        survey.visit(CENTER, -1, 1, visits::add);
        assertEquals(CENTER,visits.get(0));
        visits.clear();
        survey.visit(CENTER, 2, 2, visits::add);
        assertEquals(CENTER,visits.get(0));
        var other = new ClaimKnowledge.Chunk("minecraft:world_europe",CENTER.x(),CENTER.z());
        visits.clear();
        survey.visit(other, 2, 3, visits::add);
        assertEquals(other,visits.get(0));
        assertTrue(visits.stream().allMatch(chunk -> chunk.dimension().equals(other.dimension())));
    }

    @Test void climateSurvivesTownyErrorsAndExistingPoliticsArePreserved() {
        var knowledge = new ClaimKnowledge();
        var political = fragment(MapMode.KINGDOMS);
        var climate = fragment(MapMode.CLIMATE);
        knowledge.survey(CENTER,List.of(political),List.of(),1);
        knowledge.survey(CENTER,null,List.of(climate),2);
        assertEquals(List.of(political,climate),knowledge.at(CENTER).fragments());
        knowledge.survey(CENTER,List.of(),List.of(),3);
        assertEquals(List.of(climate),knowledge.at(CENTER).fragments());
    }

    private static boolean assertNew(Set<ClaimKnowledge.Chunk> seen,ClaimKnowledge.Chunk chunk) {
        assertTrue(seen.add(chunk),"Duplicate sample "+chunk);
        return true;
    }

    private static ClaimKnowledge.Fragment fragment(MapMode mode) {
        return new ClaimKnowledge.Fragment(new ClaimKnowledge.Label(mode,mode.name(),"Example",WORLD,1,16,"",0),
            Set.of(new ClaimGeometry.Cell(CENTER.x(),CENTER.z())));
    }
}
