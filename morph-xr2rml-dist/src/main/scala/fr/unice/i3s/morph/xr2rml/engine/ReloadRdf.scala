package fr.unice.i3s.morph.xr2rml.engine

import java.io.FileWriter
import org.apache.commons.cli.BasicParser
import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.CommandLineParser
import org.apache.log4j.Logger
import org.apache.log4j.PropertyConfigurator
import com.hp.hpl.jena.rdf.model.ModelFactory
import com.hp.hpl.jena.util.FileManager
import es.upm.fi.dia.oeg.morph.base.Constants
import es.upm.fi.dia.oeg.morph.base.MorphProperties
import es.upm.fi.dia.oeg.morph.base.engine.MorphBaseRunner
import es.upm.fi.dia.oeg.morph.base.engine.MorphBaseRunnerFactory
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter

object ReloadRdf {

    // Log4j init
    val log4jfile = this.getClass().getClassLoader().getResource("xr2rml-log4j.properties")
    println("Loading log4j configuration: " + log4jfile)
    PropertyConfigurator.configure(log4jfile)
    val logger = Logger.getLogger(this.getClass());

    def main(args: Array[String]) {
        try {
            var fileName = "C:/Users/fmichel/Documents/Projets/Zoomathia/TAXREF/TAXREF_Exemple_skos.ttl"
            var model = ModelFactory.createDefaultModel().read(FileManager.get().open(fileName), null, Constants.DEFAULT_OUTPUT_FORMAT)
            model.write(System.out, Constants.DEFAULT_OUTPUT_FORMAT, null)
        } catch {
            case e: Exception => {
                logger.fatal("An unexpected exception occured: " + e.getMessage())
                e.printStackTrace()
                System.exit(-1)
            }
        }
    }
}