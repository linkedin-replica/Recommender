package tests;

import com.arangodb.ArangoCursor;
import com.arangodb.velocypack.VPackSlice;
import database.ArangoHandler;
import database.DatabaseHandler;
import database.DatabaseSeed;
import models.Article;
import models.JobListing;
import org.json.simple.parser.ParseException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import sun.security.ssl.Debug;
import utils.ConfigReader;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;

public class ArangoHandlerTest {
    private static DatabaseSeed databaseSeed;
    private static ConfigReader config;
    private static DatabaseHandler databaseHandler;

    @BeforeClass
    public static void setup() throws IOException, ParseException, SQLException, ClassNotFoundException {
        databaseSeed = new DatabaseSeed();
        config = new ConfigReader("arango_names");
        databaseSeed.insertJobs();
        databaseSeed.insertUsers();
        databaseSeed.insertArticles();
        databaseHandler = new ArangoHandler();
    }

    @AfterClass
    public static void teardown() throws IOException {
        String dbName = config.getConfig("db.name");
        databaseSeed.deleteAllJobs();
        databaseSeed.deleteAllUsers();
        databaseSeed.dropDatabase(dbName);
    }

    @Test
    public void testRecommendUsers() {
        //TODO
    }

    @Test
    public void testRecommendJobListing() throws IOException {
        assertEquals("Recommended job listing should have at least one skill in common with user skills", true, jobsMatchingUser("0") == 4);
        assertEquals("Recommended job list for user with id 3 should be empty", true, jobsMatchingUser("3") == 0);
    }

    @Test
    public void testRecommendTrendingArticles() throws IOException, ParseException {
        ArrayList<Article> trendingArticles = databaseHandler.getTrendingArticles("0");
        int likesWeight = Integer.parseInt(config.getConfig("weights.like"));
        int commentsWeight = Integer.parseInt(config.getConfig("weights.comment"));
        int sharesWeight = Integer.parseInt(config.getConfig("weights.share"));
        int numTrendingArticles = Integer.parseInt(config.getConfig("count.trendingArticles"));

        assertEquals("Trending articles should have at most " + numTrendingArticles + "values", true, trendingArticles.size() <= numTrendingArticles);
        Debug.println("articles", trendingArticles.toString());

        for (Article article :
                trendingArticles) {
            assertEquals("Trending article should have an Id", true, article.getPostId() != null);
            assertEquals("Trending article should have an authorId", true, article.getAuthorId() != null);
            assertEquals("Trending article should have a title", true, article.getTitle() != null);
            assertEquals("Trending article should have an authorFirstName", true, article.getAuthorFirstName() != null);
            assertEquals("Trending article should have an authorLastName", true, article.getAuthorLastName() != null);
            assertEquals("Trending article should have miniText", true, article.getMiniText() != null);

            ArangoCursor<VPackSlice> cursor = databaseHandler.getArticleById(article.getPostId());
            VPackSlice expectedArticle = cursor.next();

            int expectedPeopleTalking = Integer.parseInt(expectedArticle.get("likesCount").toString()) * likesWeight
                    + Integer.parseInt(expectedArticle.get("commentsCount").toString()) * commentsWeight
                    + expectedArticle.get("shares").size() * sharesWeight;

            assertEquals("Trending article should have peopleTalking count", expectedPeopleTalking, article.getPeopleTalking());
        }
    }

    /**
     * Get the number of jobs matching some user (have skills in common)
     *
     * @param userId id of the user to check matching jobs for
     * @return number of matching jobs
     * @throws IOException
     */
    public int jobsMatchingUser(String userId) throws IOException {
        ArangoCursor<VPackSlice> cursor = databaseHandler.getUserById(userId);
        VPackSlice userSkills = cursor.next().get("skills");
        ArrayList<JobListing> recommendedJobs = databaseHandler.getRecommendedJobListing(userId);
        int jobs = 0;
        for (JobListing jobListing : recommendedJobs) {
            LOOP:
            for (String skill : jobListing.getRequiredSkills()) {
                for (int i = 0; i < userSkills.size(); ++i) {
                    String userSkill = userSkills.get(i).getAsString();
                    if (userSkill.equals(skill)) {
                        jobs++;
                        break LOOP;
                    }
                }
            }
        }
        return jobs;
    }


}