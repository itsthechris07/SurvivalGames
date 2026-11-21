package com.christian34.survivalgames.files;

import com.christian34.survivalgames.Debug;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * config.yml: the settings of the server owner, created and updated from the bundled defaults. Texts may refer to
 * environment variables - ${NAME} is replaced with the variable NAME, so secrets like the password of the database
 * don't have to be in the file.
 *
 * @author Christian34
 */
public class ConfigFile extends ResourceFile {
    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");
    /**
     * the variables that weren't set - the warning comes once
     */
    private static final Set<String> MISSING = ConcurrentHashMap.newKeySet();
    private static Function<String, String> environment = System::getenv;

    ConfigFile() {
        // the rules of the quells are the server owner's (deleted ones don't come back)
        super("config.yml", "modules.quell.rules");
    }

    /**
     * @return the text with the values of the environment variables it refers to
     */
    @Override
    @Nullable
    public String getString(@NotNull String key) {
        return resolve(key, super.getString(key));
    }

    /**
     * ${NAME} is replaced with the environment variable NAME (a variable that isn't set: nothing, with a warning that
     * names the variable, never a value)
     */
    @Nullable
    static String resolve(String key, @Nullable String value) {
        if (value == null || !value.contains("${")) return value;
        Matcher matcher = VARIABLE.matcher(value);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String variable = environment.apply(name);
            if (variable == null) {
                if (MISSING.add(name)) Debug.warn(key + " refers to the environment variable " + name + ", which isn't set");
                variable = "";
            } else {
                MISSING.remove(name);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(variable));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * the environment variables (tests can't set real ones)
     */
    static void setEnvironment(Function<String, String> environment) {
        ConfigFile.environment = environment;
        MISSING.clear();
    }

}
