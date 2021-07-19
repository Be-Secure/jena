/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jena.sparql.exec;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.jena.atlas.lib.Pair;
import org.apache.jena.atlas.logging.Log;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.query.*;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.EngineLib;
import org.apache.jena.sparql.engine.QueryEngineFactory;
import org.apache.jena.sparql.engine.QueryEngineRegistry;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.syntax.syntaxtransform.QueryTransformOps;
import org.apache.jena.sparql.util.Context;
import org.apache.jena.sparql.util.Symbol;

/**
 * Query execution for local datasets - builder style.
 */
public class QueryExecBuilder {
    // Had been migrated.
    // May have evolved.
    // Improvements to QueryExecutionBuilder

    /** Create a new builder of {@link QueryExecution} for a local dataset. */
    public static QueryExecBuilder newBuilder() {
        QueryExecBuilder builder = new QueryExecBuilder();
        return builder;
    }

    private static final long UNSET         = -1;

    private DatasetGraph dataset            = null;
    private Query        query              = null;
    // Items added with "set(,)"
    private Context      addedContext       = new Context();
    // Explicitly given base context (defaults to a copy of ARQ.getContext() merged with dataset context.
    private Context      baseContext        = null;
    // Migration - context when built, but available early to QueryExecution
    private Context      builtContext        = null;

    private Binding      initialBinding     = null;
    private long         timeout1           = UNSET;
    private TimeUnit     timeoutTimeUnit1   = null;
    private long         timeout2           = UNSET;
    private TimeUnit     timeoutTimeUnit2   = null;

    private QueryExecBuilder() { }

    public QueryExecBuilder query(Query query) {
        this.query = query;
        return this;
    }

    public QueryExecBuilder query(String queryString) {
        query(queryString, Syntax.syntaxARQ);
        return this;
    }

    public QueryExecBuilder query(String queryString, Syntax syntax) {
        this.query = QueryFactory.create(queryString, syntax);
        return this;
    }

    public QueryExecBuilder dataset(DatasetGraph dsg) {
        this.dataset = dsg;
        return this;
    }

    public QueryExecBuilder graph(Graph graph) {
        DatasetGraph dsg = DatasetGraphFactory.wrap(graph);
        dataset(dsg);
        return this;
    }

    public QueryExecBuilder set(Symbol symbol, Object value) {
        addedContext.set(symbol, value);
        return this;
    }

    public QueryExecBuilder set(Symbol symbol, boolean value) {
        addedContext.set(symbol, value);
        return this;
    }

    public QueryExecBuilder context(Context context) {
        this.baseContext = context;
        this.addedContext.clear();
        return this;
    }

    // Help with QueryExec migration.
    // This allows the build context to be available to QueryExecutionCompact
    // To be removed!
    /** @deprecated Do not use - migration only */
    @Deprecated
    public Context setupContext() {
        return builtContext();
    }

    private Context builtContext() {
        if ( builtContext == null )
            builtContext = buildContext(baseContext, dataset, addedContext);
        return builtContext;
    }

    public QueryExecBuilder initialBinding(Binding binding) {
        this.initialBinding = binding;
        return this;
    }

    public QueryExecBuilder timeout(long value, TimeUnit timeUnit) {
        this.timeout1 = value;
        this.timeoutTimeUnit1 = timeUnit;
        this.timeout2 = value;
        this.timeoutTimeUnit2 = timeUnit;
        return this;
    }

    public QueryExecBuilder initialTimeout(long value, TimeUnit timeUnit) {
        this.timeout1 = value < 0 ? -1L : value ;
        this.timeoutTimeUnit1 = timeUnit;
        return this;
    }

    public QueryExecBuilder overallTimeout(long value, TimeUnit timeUnit) {
        this.timeout2 = value;
        this.timeoutTimeUnit2 = timeUnit;
        return this;
    }

    // Set times from context if not set directly.
    private static void defaultTimeoutsFromContext(QueryExecBuilder builder, Context cxt) {
        applyTimeouts(builder, cxt.get(ARQ.queryTimeout));
    }

    /** Take obj, find the timeout(s) and apply to the builder */
    public static void applyTimeouts(QueryExecBuilder builder, Object obj) {
        if ( obj == null )
            return ;
        try {
            if ( obj instanceof Number ) {
                long x = ((Number)obj).longValue();
                if ( builder.timeout2 < 0 )
                    builder.overallTimeout(x, TimeUnit.MILLISECONDS);
            } else if ( obj instanceof String ) {
                String str = obj.toString();
                Pair<Long, Long> pair = EngineLib.parseTimoutStr(str, TimeUnit.MILLISECONDS);
                if ( builder.timeout1 < 0 )
                    builder.initialTimeout(pair.getLeft(), TimeUnit.MILLISECONDS);
                if ( builder.timeout2 < 0 )
                    builder.overallTimeout(pair.getRight(), TimeUnit.MILLISECONDS);
            } else
                Log.warn(builder, "Can't interpret timeout: " + obj);
        } catch (Exception ex) {
            Log.warn(builder, "Exception setting timeouts (context) from: "+obj);
        }
    }

    public QueryExec build() {
        Objects.requireNonNull(query, "Query for QueryExecution");
        query.setResultVars();
        Context cxt = builtContext();

        QueryEngineFactory f = QueryEngineRegistry.get().find(query, dataset, cxt);
        if ( f == null ) {
            Log.warn(QueryExecBuilder.class, "Failed to find a QueryEngineFactory");
            return null;
        }

        // Initial bindings / parameterized query
        Query queryActual = query;
        Binding initialToEngine = initialBinding;
        if ( false && initialBinding != null ) {
            // [QExec] Need CONSTRUCT fix.
            // Do by rewrite - need
            Map<Var, Node> substitutions = bindingToMap(initialBinding);
            queryActual = QueryTransformOps.transform(query, substitutions);
            initialToEngine = null;
        }

        defaultTimeoutsFromContext(this, cxt);
        QueryExec qExec = new QueryExecDataset(queryActual, dataset, cxt, f, timeout1, timeoutTimeUnit1, timeout2, timeoutTimeUnit2, initialToEngine);
        return qExec;
    }

    private Context dftContext() {
        return Context.setupContextForDataset(ARQ.getContext(), dataset) ;
    }

    private static Context buildContext(Context baseContext, DatasetGraph dataset, Context addedContext) {
        // Default is to take the global context, the copy it and merge in the dataset context.
        // If a context is specified by context(Context), use that as given.
        // The query context is modified to insert the current time.
        // This copy-isolates.

        Context cxt;
        if ( baseContext == null )
            cxt = Context.setupContextForDataset(ARQ.getContext(), dataset) ;
        else
            cxt = baseContext;
        if ( addedContext != null )
            cxt.putAll(addedContext);
        return cxt;
    }

    // ==> BindingUtils
    /** Binding as a Map */
    public static Map<Var, Node> bindingToMap(Binding binding) {
        Map<Var, Node> substitutions = new HashMap<>();
        Iterator<Var> iter = binding.vars();
        while(iter.hasNext()) {
            Var v = iter.next();
            Node n = binding.get(v);
            substitutions.put(v, n);
        }
        return substitutions;
    }

    // (Slightly shorter) abbreviated forms - build-execute now.

    public RowSet select() {
        return build().select();
    }

    public void select(Consumer<Binding> rowAction) {
        if ( !query.isSelectType() )
            throw new QueryExecException("Attempt to execute SELECT for a "+query.queryType()+" query");
        try ( QueryExec qExec = build() ) {
            forEachRow(qExec.select(), rowAction);
        }
    }

    // Also in RDFLink
    private static void forEachRow(RowSet rowSet, Consumer<Binding> rowAction) {
        rowSet.forEachRemaining(rowAction);
    }

    public Graph construct() {
        if ( !query.isConstructType() )
            throw new QueryExecException("Attempt to execute CONSTRUCT for a "+query.queryType()+" query");
        try ( QueryExec qExec = build() ) {
            return qExec.construct();
        }
    }

    public Graph describe() {
        if ( !query.isDescribeType() )
            throw new QueryExecException("Attempt to execute DESCRIBE for a "+query.queryType()+" query");
        try ( QueryExec qExec = build() ) {
            return qExec.describe();
        }
    }

    public boolean ask() {
        if ( !query.isAskType() )
            throw new QueryExecException("Attempt to execute ASK for a "+query.queryType()+" query");
        try ( QueryExec qExec = build() ) {
            return qExec.ask();
        }
    }
}
