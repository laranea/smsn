package net.fortytwo.smsn.brain;

import net.fortytwo.smsn.SemanticSynchrony;
import net.fortytwo.smsn.brain.io.wiki.WikiParser;
import net.fortytwo.smsn.brain.model.entities.Atom;
import net.fortytwo.smsn.brain.model.TopicGraph;
import net.fortytwo.smsn.brain.model.Filter;
import net.fortytwo.smsn.brain.model.Note;
import net.fortytwo.smsn.brain.model.pg.GraphWrapper;
import net.fortytwo.smsn.brain.model.pg.Neo4jGraphWrapper;
import net.fortytwo.smsn.brain.model.pg.PGTopicGraph;
import net.fortytwo.smsn.brain.model.pg.TinkerGraphWrapper;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import static org.junit.Assert.assertTrue;

public abstract class BrainTestBase {

    protected TreeViews queries;
    protected TopicGraph topicGraph;
    protected final WikiParser wikiParser = new WikiParser();

    protected Graph graph;
    protected GraphWrapper graphWrapper;
    protected Filter filter = Filter.noFilter();
    protected Collection<Atom> result;

    protected abstract TopicGraph createAtomGraph() throws IOException;

    protected TopicGraph createTinkerAtomGraph() {
        graph = TinkerGraph.open();
        graphWrapper = new TinkerGraphWrapper((TinkerGraph) graph);
        return new PGTopicGraph(graphWrapper);
    }

    protected TopicGraph createNeo4jAtomGraph() throws IOException {
        File dir = createTempDirectory();

        graphWrapper = new Neo4jGraphWrapper(dir);
        graph = graphWrapper.getGraph();

        return new PGTopicGraph(graphWrapper);
    }

    @Before
    public void setUp() throws Exception {
        topicGraph = createAtomGraph();
        Brain brain = new Brain(topicGraph);
        queries = new TreeViews(brain);
        filter = Filter.noFilter();
    }

    @After
    public void tearDown() throws Exception {
        if (null != graphWrapper) {
            graphWrapper.shutdown();
        }
    }

    protected Note importNoteFromFile(final String exampleFile) throws IOException {
        return wikiParser.parse(Brain.class.getResourceAsStream(exampleFile));
    }

    protected Atom importAtomFromFile(final String exampleFile) throws IOException {
        Filter writeFilter = new Filter(0f, 1f, 0.5f, 0f, 1f, 0.5f);
        TreeViews.ViewStyle style = TreeViews.forwardViewStyle;

        Note rootNote = importNoteFromFile(exampleFile);
        rootNote.setId(SemanticSynchrony.createRandomId());
        Atom root = topicGraph.createAtomWithProperties(filter, rootNote.getId());
        queries.update(rootNote, 5, writeFilter, style);
        return root;
    }

    protected int countAtoms(final TopicGraph topicGraph) {
        int count = 0;
        for (Atom a : topicGraph.getAllAtoms()) {
            count++;
        }
        return count;
    }

    private File createTempDirectory() throws IOException {
        File file = File.createTempFile("smsn-testing-", "");
        file.delete();

        file.mkdirs();
        file.deleteOnExit();
        assertTrue(file.exists());
        assertTrue(file.isDirectory());
        return file;
    }
}
