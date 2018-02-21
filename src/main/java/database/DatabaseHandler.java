package database;

import models.JobListing;
import models.Article;
import models.User;

import java.util.ArrayList;

public interface DatabaseHandler {

    /**
     * Initiate a connection with the database
     */
    void connect();


    /**
     * Get the recommended users for a specific user
     *
     * @param userId: the user seeking friend recommendations
     * @return list of recommended users
     */
    ArrayList<User> getRecommendedUsers(int userId);


    /**
     * Get the recommended job listings for a specific user
     *
     * @param userId: the user seeking job listing recommendations
     * @return list of recommended job listings
     */
    ArrayList<JobListing> getRecommendedJobListing(int userId);


    /**
     * Get the trending articles for a specific user
     *
     * @param userId: the user seeking trending articles
     * @return list of trending articles
     */
    ArrayList<Article> getTrendingArticles(int userId);


    /**
     * Close a connection with the database
     */
    void disconnect();
}