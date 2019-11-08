package r2rml.jena;

import org.apache.jena.assembler.Assembler;
import org.apache.jena.assembler.Mode;
import org.apache.jena.assembler.assemblers.ModelAssembler;
import org.apache.jena.assembler.exceptions.AssemblerException;
import org.apache.jena.rdf.model.*;
import org.apache.jena.sparql.util.MappingRegistry;
import r2rml.engine.Configuration;
import r2rml.engine.R2RMLProcessor;

import java.util.logging.Logger;

/**
 * R2RML Model Assembler
 *  Processes R2RML mapping and adds the output triples to the base model
 *
 * @author Maxim Van de Wynckel (mvdewync@vub.be)
 */
public class R2RMLAssembler extends ModelAssembler {
    private static Logger logger = Logger.getLogger(R2RMLAssembler.class.getName());
    private static boolean initialized = false;

    // R2RML URI
    private static String URI = "http://r2rml#";

    static {
        init();
    }

    static synchronized public void init() {
        if (initialized)
            return;
        logger.info("Initializing R2RML Assembler ...");
        // TODO: Load SQLite drivers in some different manner
        try { Class.forName("org.sqlite.JDBC").newInstance(); } catch(Exception e) {e.printStackTrace();};
        // Register prefix
        MappingRegistry.addPrefixMapping("r2rml", URI) ;
        // Register this model assembler as "http://r2rml#Model"
        Assembler.general.implementWith(ResourceFactory.createProperty(URI + "Model"), new R2RMLAssembler());
        logger.info("Registered Assembler model: " + URI + "Model");
        initialized = true;
    }

    @Override
    public Model open(Assembler a, Resource root, Mode mode) {
        logger.info("Processing R2RML Jena Assembler ...");
        // Get the ja:baseModel
        Resource rootModel = getUniqueResource(root, ResourceFactory.createProperty(URI + "baseModel"));
        if (rootModel == null) {
            throw new AssemblerException( root, "No r2rml:baseModel specified!");
        }
        Model baseModel = a.openModel(rootModel, Mode.ANY);

        // Get the configuration from the root resource
        Literal confMappingFile = getUniqueLiteral(root, ResourceFactory.createProperty(URI + "mappingFile"));
        if (confMappingFile == null){
            throw new AssemblerException( root, "No r2rml:mappingFile specified!");
        }
        Literal confConnectionURL = getUniqueLiteral(root, ResourceFactory.createProperty(URI + "connectionURL"));
        if (confConnectionURL == null){
            throw new AssemblerException( root, "No r2rml:connectionURL specified!");
        }
        Model dataModel = ModelFactory.createDefaultModel();

        // Run the R2RML processor
        Configuration configuration = new Configuration();
        configuration.setMappingFile(confMappingFile.getString());
        configuration.setConnectionURL(confConnectionURL.getString());
        R2RMLProcessor engine = new R2RMLProcessor(configuration);
        engine.execute();
        dataModel.add(engine.getDataset().getDefaultModel());

        // Add the data model (R2RML processed results) to the base model
        baseModel.add(dataModel);
        return baseModel;
    }

    @Override
    protected Model openEmptyModel(Assembler a, Resource root, Mode mode) {
        return open(a, root, mode);
    }
}
