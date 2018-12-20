package com.linkedin.replica.recommender.database.handlers.impl;


import com.arangodb.ArangoCursor;
import com.arangodb.ArangoDB;
import com.arangodb.ArangoDatabase;
import com.arangodb.util.MapBuilder;
import com.arangodb.velocypack.VPackSlice;
import com.linkedin.replica.recommender.models.Article;
import com.linkedin.replica.recommender.models.JobListing;
import com.linkedin.replica.recommender.models.User;
import com.linkedin.replica.recommender.utils.Configuration;
import com.linkedin.replica.recommender.database.DatabaseConnection;
import com.linkedin.replica.recommender.database.handlers.RecommendationDatabaseHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

public class ArangoDatabaseHandler implements RecommendationDatabaseHandler {
    private ArangoDatabase dbInstance;

    public ArangoDatabaseHandler() throws IOException {
        // init db
        ArangoDB arangoDriver = DatabaseConnection.getInstance().getArangoDriver();
        dbInstance = arangoDriver.db(Configuration.getInstance().getArangoConfig("db.name"));
    }

    /**
     * Get list of friends of friends
     * @param userId
     * @return ArrayList of Users
     */
    public ArrayList<User> getFriendsOfFriends(String userId) {
        String query = "FOR u IN users FILTER "
                + "u.userId == @userId " +
                "LET result = ( " +
                "FOR friend IN users FILTER " +
                "friend.userId IN u.friendsList " +
                "FOR friendOfFriend IN users FILTER " +
                "friendOfFriend.userId IN friend.friendsList AND friendOfFriend.userId NOT IN u.friendsList " +
                "RETURN friendOfFriend " +
                ") " +
                "FOR res IN UNIQUE(result) " +
                "RETURN res";
        Map<String, Object> bindVars = new MapBuilder().put("userId", userId).get();
        ArangoCursor<User> cursor = dbInstance.query(query, bindVars, null, User.class);
        ArrayList<User> users = new ArrayList<>(cursor.asListRemaining());
        return users;
    }

    /**
     * Get the recommended job listings for a specific user
     *
     * @param userId : the user seeking job listing recommendations
     * @return list of recommended job listings
     */
    public ArrayList<JobListing> getRecommendedJobListing(String userId) throws IOException {

        //query for getting jobListing if there is a match between this jobListing and requesting user's skills
        String query = "FOR user in users " +
                "FILTER user.userId == @userId " +
                "FOR job IN jobs FILTER " +
                "COUNT(INTERSECTION(user.skills, job.requiredSkills)) != 0 " +
                "FOR company in companies " +
                "FILTER company.companyId == job.companyId " +
                "return UNSET(MERGE_RECURSIVE( " +
                "job," +
                "{\"companyName\": company.companyName, \"profilePictureUrl\": company.profilePictureUrl} " +
                "), \"_id\", \"_rev\", \"_key\")";
        //bind variables
        Map<String, Object> bindVars = new MapBuilder().put("userId", userId).get();
        ArangoCursor<JobListing> cursor = dbInstance.query(query, bindVars, null, JobListing.class);
        ArrayList<JobListing> returnedResults = new ArrayList<>(cursor.asListRemaining());
        return returnedResults;
    }

    public ArangoCursor<VPackSlice> getUserById(String userId) throws IOException {
        String query = "FOR u IN users FILTER "
                + "u.userId == @userId "
                + "RETURN u";
        Map<String, Object> bindVars = new MapBuilder().put("userId", userId).get();
        return dbInstance.query(query, bindVars, null, VPackSlice.class);
    }

    public ArangoCursor<VPackSlice> getArticleById(String postId) throws IOException {
        String query = "FOR article IN posts FILTER "
                + "article.postId == @postId "
                + "RETURN article";
        Map<String, Object> bindVars = new MapBuilder().put("postId", postId).get();
        return dbInstance.query(query, bindVars, null, VPackSlice.class);
    }


    /**
     * Get the trending articles for a specific user
     *
     * @param userId : the user seeking trending articles
     * @return list of trending articles
     */
    public ArrayList<Article> getTrendingArticles(String userId) throws IOException {

        Configuration config = Configuration.getInstance();
        int likesWeight = Integer.parseInt(config.getArangoConfig("weights.like"));
        int commentsWeight = Integer.parseInt(config.getArangoConfig("weights.comment"));
        int numTrendingArticles = Integer.parseInt(config.getArangoConfig("count.trendingArticles"));

        //query for getting the top n articles sorted by likes, comments and shares with their weights from the most recent 50 articles
        String queryHeader = "FOR article IN posts FILTER article.isArticle == true ";

        String getAuthorQuery = "LET author = FIRST(FOR u IN users " +
                "FILTER u.userId == article.authorId " +
                "RETURN u) ";

        String sortArticlesByTimeQuery = "SORT article.timestamp DESC "
                + "LIMIT 0, 50 ";

        String sortArticlesByPopularityQuery = "LET comments = article.commentsCount * @commentsWeight "
                + "LET likes = COUNT(article.likers) * @likesWeight "
                + "LET total = likes + comments "
                + "SORT total DESC "
                + "LIMIT 0, @numArticles ";

        String userDetailsQuery = "FOR u in users FILTER u.userId == article.authorId " +
                "LET authorName = CONCAT(u.firstName, \" \",  u.lastName) " +
                "LET authorProfilePictureUrl = u.profilePictureUrl " +
                "LET headline = u.headline ";

        String getLikersQuery = "LET likers = ( " +
                "FOR liker in users " +
                "FILTER liker._key IN article.likers " +
                "RETURN " +
                "{ " +
                "\"likerId\": liker._key, " +
                "\"likerName\": CONCAT(liker.firstName, \" \", liker.lastName), " +
                "\"likerProfilePictureUrl\": liker.profilePictureUrl " +
                "} " +
                ") ";

        String returnQuery = "RETURN MERGE_RECURSIVE(UNSET(article, \"text\", \"liked\", \"isArticle\", \"likers\", \"peopleTalking\"), " +
                "{\"authorName\": authorName," +
                "\"headline\":headline," +
                "\"authorProfilePictureUrl\": authorProfilePictureUrl," +
                "\"miniText\": LEFT(article.text, 200)," +
                "\"liked\": u.userId IN article.likers," +
                "\"likers\": likers," +
                "\"peopleTalking\": total" +
                "})";

        String query = queryHeader + getAuthorQuery + sortArticlesByTimeQuery + sortArticlesByPopularityQuery
                + userDetailsQuery + getLikersQuery + returnQuery;

        //bind variables
        Map<String, Object> bindVars = new MapBuilder()
                .put("likesWeight", likesWeight)
                .put("commentsWeight", commentsWeight)
                .put("numArticles", numTrendingArticles)
                .get();
        //execute query
        ArangoCursor<Article> cursor = dbInstance.query(query, bindVars, null, Article.class);
        ArrayList<Article> returnedResults = new ArrayList<>(cursor.asListRemaining());
        return returnedResults;
    }

    /**
     * Close a connection with the database
     */
    public void disconnect() {
        //TODO
    }
}
