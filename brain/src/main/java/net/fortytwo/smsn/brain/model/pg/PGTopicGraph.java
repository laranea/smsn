package net.fortytwo.smsn.brain.model.pg;

import com.google.common.base.Preconditions;
import net.fortytwo.smsn.SemanticSynchrony;
import net.fortytwo.smsn.brain.model.Filter;
import net.fortytwo.smsn.brain.model.TopicGraph;
import net.fortytwo.smsn.brain.model.entities.*;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class PGTopicGraph implements TopicGraph {

    private static final String REDACTED_VALUE = "";

    private static final String thingNamespace = SemanticSynchrony.getConfiguration().getThingNamespace();

    private final GraphWrapper wrapper;
    private final Graph propertyGraph;

    private long lastUpdate;

    public PGTopicGraph(final GraphWrapper wrapper) {
        this.wrapper = wrapper;
        this.propertyGraph = wrapper.getGraph();
    }

    public Graph getPropertyGraph() {
        return propertyGraph;
    }

    public String idOfAtom(final Atom a) {
        return a.getId();
    }

    // TODO: move me
    public static String iriForId(final String id) {
        return thingNamespace + id;
    }

    public String iriOfAtom(final Atom a) {
        return iriForId(idOfAtom(a));
    }

    @Override
    public long getLastUpdate() {
        return lastUpdate;
    }

    @Override
    public void begin() {
        wrapper.begin();
    }

    @Override
    public void commit() {
        wrapper.commit();
    }

    @Override
    public void rollback() {
        wrapper.rollback();
    }

    @Override
    public TopicGraph createFilteredGraph(Filter filter) {
        //return new FilteredAtomGraph(this, filter);
        return copyGraph(filter);
    }

    @Override
    public void notifyOfUpdate() {
        this.lastUpdate = System.currentTimeMillis();
    }

    @Override
    public Optional<Atom> getAtomById(final String id) {
        Vertex v = wrapper.getVertexById(id);

        return null == v ? Optional.empty() : Optional.of(asAtom(v));
    }

    private <T extends Entity> EntityList<T> createListOfEntities(final String vertexLabel,
                                                   final Function<Vertex, EntityList<T>> constructor,
                                                   final T[] elements) {
        Preconditions.checkArgument(elements.length > 0);

        EntityList<T> last = null;
        EntityList<T> head = null;
        for (T el : elements) {
            EntityList<T> cur = createEntity(null, vertexLabel, constructor);
            cur.setFirst(el);

            if (null == head) {
                head = cur;
            }
            if (last != null) {
                last.setRest(cur);
            }
            last = cur;
        }

        return head;
    }

    @Override
    public EntityList<Link> createListOfLinks(final Link... elements) {
        return createListOfEntities(SemanticSynchrony.VertexLabels.LIST, this::asListOfLinks, elements);
    }

    @Override
    public EntityList<KeyValueTree<Link, EntityList<Link>>> createListOfTrees(
            KeyValueTree<Link, EntityList<Link>>... elements) {
        return createListOfEntities(SemanticSynchrony.VertexLabels.LIST, this::asListOfLinkTrees, elements);
    }

    @Override
    public EntityList<Atom> createListOfAtoms(final Atom... elements) {
        return createListOfEntities(SemanticSynchrony.VertexLabels.LIST, this::asListOfAtoms, elements);
    }

    @Override
    public Topic createTopic(final String topicId) {
        Topic topic = createEntity(null, SemanticSynchrony.VertexLabels.TOPIC, this::asTopic);
        topic.setId(topicId);
        return topic;
    }

    @Override
    public Page createPage(final Link topicLink) {
        Topic topic = topicLink.getTarget();
        Page page = createEntity(null, SemanticSynchrony.VertexLabels.PAGE, this::asPage);
        page.setPrimaryTopic(topic);
        page.setContent(createTopicTree(topicLink));
        return page;
    }

    @Override
    public Link createLink(Topic target, String label) {
        Link link = createEntity(null, SemanticSynchrony.VertexLabels.LINK, this::asLink);
        link.setTarget(target);
        link.setLabel(label);
        return link;
    }

    @Override
    public KeyValueTree<Link, EntityList<Link>> createTopicTree(final Link link) {
        KeyValueTree<Link, EntityList<Link>> tree
                = createEntity(null, SemanticSynchrony.VertexLabels.TREE, this::asLinkTree);
        tree.setKey(link);
        return tree;
    }

    @Override
    public Atom createAtom(final String id) {
        return createEntity(id, SemanticSynchrony.VertexLabels.ATOM, this::asAtom);
    }

    @Override
    public Atom createAtomWithProperties(final Filter filter,
                                         final String id) {

        Atom atom = createAtom(id);

        atom.setCreated(new Date().getTime());
        atom.setSource(filter.getDefaultSource());
        atom.setWeight(filter.getDefaultWeight());

        return atom;
    }

    public Topic asTopic(final Vertex vertex) {
        Preconditions.checkNotNull(vertex, "vertex");

        return new PGTopic(vertex) {
            @Override
            protected PGTopicGraph getGraph() {
                return PGTopicGraph.this;
            }
        };
    }

    public Link asLink(final Vertex vertex) {
        Preconditions.checkNotNull(vertex, "vertex");

        return new PGLink(vertex) {
            @Override
            protected PGTopicGraph getGraph() {
                return PGTopicGraph.this;
            }
        };
    }

    public Page asPage(final Vertex vertex) {
        Preconditions.checkNotNull(vertex, "vertex");

        return new PGPage(vertex) {
            @Override
            protected PGTopicGraph getGraph() {
                return PGTopicGraph.this;
            }
        };
    }

    public Atom asAtom(final Vertex vertex) {
        Preconditions.checkNotNull(vertex, "vertex");

        return new PGAtom(vertex) {
            @Override
            protected PGTopicGraph getGraph() {
                return PGTopicGraph.this;
            }
        };
    }

    public <T extends Entity> EntityList<T> asEntityList(final Vertex vertex, final Function<Vertex, T> constructor) {
        Preconditions.checkNotNull(vertex, "vertex");

        return new PGEntityList<T>(vertex, constructor) {
            @Override
            protected PGTopicGraph getGraph() {
                return PGTopicGraph.this;
            }
        };
    }

    public <K extends Entity, V extends Entity> KeyValueTree<K, V> asEntityTree(final Vertex vertex,
                                                  final Function<Vertex, K> keyConstructor,
                                                  final Function<Vertex, V> valueConstructor) {
        Preconditions.checkNotNull(vertex, "vertex");

        return new PGKeyValueTree<K, V>(vertex, keyConstructor, valueConstructor) {
            @Override
            protected PGTopicGraph getGraph() {
                return PGTopicGraph.this;
            }
        };
    }

    public EntityList<Link> asListOfLinks(final Vertex vertex) {
        return asEntityList(vertex, this::asLink);
    }

    public EntityList<KeyValueTree<Link, EntityList<Link>>> asListOfLinkTrees(final Vertex vertex) {
        return asEntityList(vertex, this::asLinkTree);
    }

    public EntityList<Atom> asListOfAtoms(final Vertex vertex) {
        return asEntityList(vertex, this::asAtom);
    }

    public KeyValueTree<Link, EntityList<Link>> asLinkTree(final Vertex vertex) {
        return asEntityTree(vertex, this::asLink, this::asListOfLinks);
    }

    @Override
    public void removeIsolatedAtoms(final Filter filter) {
        Preconditions.checkNotNull(filter, "filter");

        List<Vertex> toRemove = new LinkedList<>();

        propertyGraph.vertices().forEachRemaining(v -> {
            if (isAtomVertex(v)
                    && !v.edges(Direction.IN).hasNext()
                    && !v.edges(Direction.OUT).hasNext()) {
                if (filter.test(asAtom(v))) {
                    toRemove.add(v);
                }
            }
        });

        // note: we assume from the above that there are no dependent vertices (i.e. list nodes) to remove first
        toRemove.forEach(Element::remove);

        notifyOfUpdate();
    }

    @Override
    public void reindexAtom(final Atom atom) {
        updateIndex(atom, SemanticSynchrony.PropertyKeys.ID_V);
        updateIndex(atom, SemanticSynchrony.PropertyKeys.TITLE);
        updateIndex(atom, SemanticSynchrony.PropertyKeys.ACRONYM);
        updateIndex(atom, SemanticSynchrony.PropertyKeys.SHORTCUT);
    }

    void updateIndex(final Atom atom, final String key) {
        wrapper.updateIndex(((PGAtom) atom).asVertex(), key);
    }

    /**
     * @return an Iterable of all atoms in the knowledge base, as opposed to all vertices
     * (many of which are list nodes rather than atoms)
     */
    @Override
    public Iterable<Atom> getAllAtoms() {
        return () -> asFilteredStream(
                getPropertyGraph().vertices(),
                this::isAtomVertex)
                .map(this::asAtom).iterator();
    }

    @Override
    public List<Atom> getAtomsByTitleQuery(final String query, final Filter filter) {
        return filterAndSort(wrapper.getVerticesByTitle(query), filter);
    }

    @Override
    public List<Atom> getAtomsByAcronym(final String acronym, final Filter filter) {
        return filterAndSort(wrapper.getVerticesByAcronym(acronym.toLowerCase()), filter);
    }

    @Override
    public List<Atom> getAtomsByShortcut(final String shortcut, final Filter filter) {
        return filterAndSort(wrapper.getVerticesByShortcut(shortcut), filter);
    }

    private boolean isAtomVertex(final Vertex v) {
        String label = v.label();
        return null != label && label.equals(SemanticSynchrony.VertexLabels.ATOM);
    }

    public PGTopicGraph copyGraph(final Filter filter) {
        GraphWrapper newWrapper = new TinkerGraphWrapper(TinkerGraph.open());
        PGTopicGraph newGraph = new PGTopicGraph(newWrapper);

        for (Atom originalAtom : getAllAtoms()) {
            if (filter.test(originalAtom)) {
                PGAtom newAtom = findOrCopyAtom(originalAtom, filter, newGraph);
                PGEntityList<Atom> children = (PGEntityList<Atom>) originalAtom.getChildren();
                if (null != children) {
                    newAtom.setChildren(copyAtomList(children, filter, newGraph));
                }
            }
        }

        return newGraph;
    }

    private <T> T createEntity(final String id, final String label, final Function<Vertex, T> constructor) {
        Vertex vertex = wrapper.createVertex(id, label);

        return constructor.apply(vertex);
    }

    private List<Atom> filterAndSort(
            final Iterator<Sortable<Vertex, Float>> unranked,
            final Filter filter) {

        List<Sortable<Atom, Float>> ranked = new LinkedList<>();
        while (unranked.hasNext()) {
            Sortable<Vertex, Float> in = unranked.next();
            Atom a = asAtom(in.getEntity());
            if (!filter.test(a)) continue;

            float nativeScore = in.getScore();
            float weight = a.getWeight();
            String value = a.getTitle();
            float lengthPenalty = Math.min(1.0f, 15.0f / value.length());
            float score = nativeScore * weight * lengthPenalty;
            ranked.add(new Sortable<>(a, score));
        }

        Collections.sort(ranked);

        return ranked.stream().map(Sortable::getEntity).collect(Collectors.toList());
    }

    private PGAtom findOrCopyAtom(final Atom original, final Filter filter, final TopicGraph newGraph) {
        Optional<Atom> opt = newGraph.getAtomById(original.getId());
        if (opt.isPresent()) return (PGAtom) opt.get();
        PGAtom newAtom = (PGAtom) newGraph.createAtomWithProperties(filter, original.getId());
        newAtom.setSource(original.getSource());

        if (filter.test(original)) {
            newAtom.setTitle(original.getTitle());
            newAtom.setWeight(original.getWeight());
            newAtom.setShortcut(original.getShortcut());
            newAtom.setPriority(original.getPriority());
            newAtom.setAlias(original.getAlias());
            newAtom.setCreated(original.getCreated());
        } else {
            newAtom.setTitle(REDACTED_VALUE);
        }

        return newAtom;
    }

    private EntityList<Atom> copyAtomList(final PGEntityList<Atom> original, final Filter filter, final PGTopicGraph newGraph) {
        PGEntityList<Atom> originalCur = original;
        PGEntityList<Atom> newHead = null, newCur, newPrev = null;
        while (null != originalCur) {
            Atom originalFirst = originalCur.getFirst();
            PGAtom newAtom = findOrCopyAtom(originalFirst, filter, newGraph);
            newCur = (PGEntityList<Atom>) newGraph.createListOfAtoms(newAtom);

            if (null == newPrev) {
                newHead = newCur;
            } else {
                newPrev.setRest(newCur);
            }

            newPrev = newCur;
            originalCur = (PGEntityList<Atom>) originalCur.getRest();
        }

        return newHead;
    }

    private <A> Stream<A> asFilteredStream(Iterator<A> sourceIterator, Predicate<A> filter) {
        Iterable<A> iterable = () -> sourceIterator;
        Stream<A> stream = StreamSupport.stream(iterable.spliterator(), false);

        return stream.filter(filter);
    }
}
