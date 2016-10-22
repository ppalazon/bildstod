package io.github.ppalazon.bildstod;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;

/**
 * User: ppalazon
 * Date: 10/21/16
 * Time: 2:01 PM
 */
public class BildstodVerticle extends AbstractVerticle
{
    @Override
    public void start() throws Exception
    {
        Config conf = ConfigFactory.load();

        HttpServer server = vertx.createHttpServer();
        EventBus eventBus = vertx.eventBus();

        Router router = Router.router(vertx);

        router.getWithRegex("\\/(\\w{8}?).(png|jpg|gif)").handler(routingContext ->
        {
            String code = routingContext.request().getParam("param0");
            String extension = routingContext.request().getParam("param1");
            String filename = code + "." + extension;
            if (conf.getString("storage.type").contentEquals("local")) {
                File storagePath = new File(conf.getString("storage.local.path"));
                if (!storagePath.exists()) {
                    throw new RuntimeException("Local storage " + conf.getString("storage.local.path") + " doesn't exists");
                }
                File imageFile = new File(storagePath, filename);
                if (!imageFile.exists()) {
                    throw new RuntimeException("File " + imageFile + " doesn't exists");
                }

                routingContext.response()
                        .sendFile(imageFile.getPath());
            }

        });

        router.put("/upload").handler(BodyHandler.create().setMergeFormAttributes(true));
        router.put("/upload").blockingHandler(routingContext ->
        {
            Set<FileUpload> fileUploadSet = routingContext.fileUploads();
            for (FileUpload fileUpload : fileUploadSet) {
                // To get the uploaded file do
                if (fileUpload.name().contentEquals("file")) {
                    String code = RandomStringUtils.random(8, true, true);
                    String extension = ".png";
                    try {
                        extension = URLDecoder.decode(fileUpload.fileName(), "UTF-8").split("\\.")[1].toLowerCase();
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    String filename = code + "." + extension;

                    if (conf.getString("storage.type").contentEquals("local")) {
                        File storagePath = new File(conf.getString("storage.local.path"));
                        if (!storagePath.exists()) {
                            storagePath.mkdir();
                        }
                        File imageFile = new File(storagePath, filename);
                        try {
                            FileUtils.copyFile(new File(fileUpload.uploadedFileName()), imageFile);
                        } catch (IOException e) {
                            throw new RuntimeException("Cannot copy files " + e.getMessage());
                        }

                        routingContext.response()
                                .setStatusCode(201)
                                .putHeader("Location", "/" + code);
                    }

                }

                // Use the Event Bus to dispatch the file now
                // Since Event Bus does not support POJOs by default so we need to create a MessageCodec implementation
                // and provide methods for encode and decode the bytes
            }
        });

        server.requestHandler(router::accept).listen(conf.getInt("server.port"));
    }
}
