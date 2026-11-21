package com.christian34.survivalgames.files;

/**
 * config.yml: the settings of the server owner, created and updated from the bundled defaults.
 *
 * @author Christian34
 */
public class ConfigFile extends ResourceFile {

    ConfigFile() {
        // the rules of the quells are the server owner's (deleted ones don't come back)
        super("config.yml", "modules.quell.rules");
    }

}
