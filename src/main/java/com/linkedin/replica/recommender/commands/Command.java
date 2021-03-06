package com.linkedin.replica.recommender.commands;


import com.linkedin.replica.recommender.database.handlers.DatabaseHandler;
import com.linkedin.replica.recommender.exceptions.BadRequestException;

import java.io.IOException;
import java.util.HashMap;

public abstract class Command {
    protected HashMap<String, Object> args;
    protected DatabaseHandler dbHandler;

    public Command(HashMap<String, Object> args) {
        this.args = args;
    }

    /**
     * Execute the command
     *
     * @return The output (if any) of the command
     */
    public abstract Object execute() throws IOException, NoSuchMethodException, IllegalAccessException;

    /**
     * Set the configured db handler
     *
     * @param dbHandler: The configured db handler
     */
    public void setDbHandler(DatabaseHandler dbHandler) {
        this.dbHandler = dbHandler;
    }


    protected void validateArgs(String[] requiredArgs) {
        for (String arg : requiredArgs)
            if (!args.containsKey(arg)) {
                String exceptionMsg = String.format("Cannot execute command. %s argument is missing", arg);
                throw new BadRequestException(exceptionMsg);
            }
    }
}