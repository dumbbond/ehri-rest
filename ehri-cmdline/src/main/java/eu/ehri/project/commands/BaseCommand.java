package eu.ehri.project.commands;

import org.apache.commons.cli.*;

import com.tinkerpop.blueprints.impls.neo4j.Neo4jGraph;
import com.tinkerpop.frames.FramedGraph;

public abstract class BaseCommand {
    
    Options options = new Options();
    CommandLineParser parser = new PosixParser();
        
    protected void setCustomOptions() {}

    public abstract String getHelp();
    public abstract String getUsage();

    public void printUsage() {
        // automatically generate the help statement
        System.err.println(getUsage());
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp( "ant", options );
    }

    public final int exec(final FramedGraph<Neo4jGraph> graph, String[] args) throws Exception {
        setCustomOptions();
        return execWithOptions(graph, parser.parse(options, args));
    }
    public abstract int execWithOptions(final FramedGraph<Neo4jGraph> graph, CommandLine cmdLine) throws Exception;
    public boolean isReadOnly() {
        return false;
    }
}
