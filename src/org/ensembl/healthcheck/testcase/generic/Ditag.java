/*
 * Copyright (C) 2006 WTSI & EBI
 *
 * This library is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this library;
 * if not, write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
 * 02111-1307 USA
 */

package org.ensembl.healthcheck.testcase.generic;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.ensembl.healthcheck.DatabaseRegistryEntry;
import org.ensembl.healthcheck.DatabaseType;
import org.ensembl.healthcheck.Species;
import org.ensembl.healthcheck.ReportManager;
import org.ensembl.healthcheck.testcase.SingleDatabaseTestCase;
import org.ensembl.healthcheck.util.DBUtils;

/**
 *
 */

public class Ditag extends SingleDatabaseTestCase {
	
    // max number of top-level seq regions to check
    private static final int MAX_TOP_LEVEL = 100;

    /**
     * Creates a new instance of Ditag.
     */
    public Ditag() {

        addToGroup("post_genebuild");
        addToGroup("release");
        setDescription("Checks that ditag_features exist, that they all have a ditag entry and that all chromosomes have some ditag_features");
        setTeamResponsible("GeneBuilders");

    }

    /**
     * This test only applies to core databases.
     */
    public void types() {

        removeAppliesToType(DatabaseType.OTHERFEATURES);
        removeAppliesToType(DatabaseType.ESTGENE);
        removeAppliesToType(DatabaseType.VEGA);
        removeAppliesToType(DatabaseType.SANGER_VEGA);        
        removeAppliesToType(DatabaseType.CDNA);
    		removeAppliesToType(DatabaseType.RNASEQ);

    }

    /**
     * Test various things about ditag features.
     *
     * @param dbre
     *            The database to use.
     * @return Result.
     */
    public boolean run(DatabaseRegistryEntry dbre) {

        boolean result = true;

        Connection con = dbre.getConnection();

        // only check for human, mouse
        Species s = dbre.getSpecies();
        if (s.equals(Species.HOMO_SAPIENS) || s.equals(Species.MUS_MUSCULUS)) {

            result &= checkExistance(con);

            result &= checkDitagRelation(con);

            result &= checkAllChromosomesHaveDitagFeatures(con);

	    result &= checkForSingles(con);

	    result &= checkForMultis(con);

        }

        return result;

    }

    // ----------------------------------------------------------------------
    /*
     * Verify ditags & ditag features exist.
     */

    private boolean checkExistance(Connection con) {

        boolean result = true;

        int rowCount1 = getRowCount(con, "SELECT * FROM ditag LIMIT 10");

        if (rowCount1 == 0) {
            ReportManager.problem(this, con, "No ditags in databaset");
            result = false;
        }

        int rowCount2 = getRowCount(con, "SELECT * FROM ditag_feature LIMIT 10");

        if (rowCount2 == 0) {
            ReportManager.problem(this, con, "No ditag features in databaset");
            result = false;
        }

        if (result) {
            ReportManager.correct(this, con, "Found entries in ditag & ditag_feature tables.");
        }

        return result;

    }

    // ----------------------------------------------------------------------
    /**
     * Check that all ditag_features have a ditag entry and that there are not more ditag mappings than allowed.
     */

    private boolean checkDitagRelation(Connection con) {

        boolean result = true;

        int count = getRowCount(con, "SELECT COUNT(*) FROM ditag_feature df LEFT JOIN ditag d ON d.ditag_id=df.ditag_id WHERE d.ditag_id IS NULL");

        if (count > 0) {

            ReportManager.problem(this, con, " There are " + count + " ditag_features without ditag entry.");
            result = false;

        } else {

            ReportManager.correct(this, con, "All ditag_features have ditag entries.");

        }

        return result;
    }

    // ----------------------------------------------------------------------
    /**
     * Check that all chromomes have > 0 ditag_features.
     */

    private boolean checkAllChromosomesHaveDitagFeatures(Connection con) {

        boolean result = true;

        // find all the chromosomes, and for each one check that it has some markers
        // note a "chromosome" is assumed to be a seq_region that is:
        // - on the top-level co-ordinate system and
        // - doesn't have and _ or . in the name and
        // - has a seq_region name of less than 3 characters
        // - doesn't have a name starting with "Un" or "MT"

        // get top level co-ordinate system ID
        String sql = "SELECT coord_system_id FROM coord_system WHERE rank=1 LIMIT 1";

        String s = getRowColumnValue(con, sql);

        if (s.length() == 0) {
            System.err.println("Error: can't get top-level co-ordinate system for " + DBUtils.getShortDatabaseName(con));
            return false;
        }

        int topLevelCSID = Integer.parseInt(s);

        try {

            // check each top-level seq_region (up to a limit) to see how many
            // marker_map_locations and marker features there are
            Statement stmt = con.createStatement();

            ResultSet rs = stmt.executeQuery("SELECT * FROM seq_region WHERE coord_system_id="
                            + topLevelCSID
                            + " AND name NOT LIKE '%\\_%' AND name NOT LIKE '%.%' AND name NOT LIKE 'Un%' AND name NOT LIKE 'MT%' AND LENGTH(name) < 3 ORDER BY name");

            int numTopLevel = 0;

            while (rs.next() && numTopLevel++ < MAX_TOP_LEVEL) {

                long seqRegionID = rs.getLong("seq_region_id");
                String seqRegionName = rs.getString("name");

                // check ditag_features
                logger.fine("Counting ditag_features on chromosome " + seqRegionName);
                sql = "SELECT COUNT(*) FROM ditag_feature WHERE seq_region_id=" + seqRegionID;
                int rows = getRowCount(con, sql);
                if (rows == 0) {

                    ReportManager.problem(this, con, "Chromosome " + seqRegionName + " (seq_region_id " + seqRegionID
                            + ") has no ditag_features");
                    result = false;

                } else {

                    ReportManager.correct(this, con, "Chromosome " + seqRegionName + " has " + rows + " ditag_features");

                }

            }

            rs.close();
            stmt.close();

            if (numTopLevel == MAX_TOP_LEVEL) {
                logger.warning("Only checked first " + numTopLevel + " seq_regions");
            }

        } catch (SQLException se) {
            se.printStackTrace();
        }

        return result;

    }


    // ----------------------------------------------------------------------
    /**
     * Check that all ditags (that are not CAGE tags) have start AND end tags mapped.
     */

    private boolean checkForSingles(Connection con) {

        boolean result      = true;
	String analysis_ids = "";

        try {

	    //Get the analysis ids of the ditags
	    Statement stmt = con.createStatement();
	    ResultSet rs   = stmt.executeQuery("SELECT DISTINCT analysis_id FROM ditag_feature;");

	    while (rs.next()) {
		String analysis_id = rs.getString("analysis_id");
		if ( analysis_ids.length() > 0 ) {
		    analysis_ids = analysis_ids + ", " + analysis_id;
		}
		else {
		    analysis_ids = analysis_id + " ";
		}
	    }

	    if (analysis_ids.length() == 0) {
		return true;
	    }

	    //Check for ditag_ids that occur only once, ignore CAGE tags ("F")
	    String sql = "SELECT COUNT(*) AS singles FROM (select count(*) as count from ditag_feature df where analysis_id IN("
		+ analysis_ids
		+ ") and df.ditag_side!='F' group by ditag_id, ditag_pair_id having count=1) as counter LIMIT 5;";
	    System.out.println(sql);
	    int count = 0;
	    rs        = stmt.executeQuery(sql);
	    rs.next();
	    count     = rs.getInt("singles");

	    if (count > 0) {

		ReportManager.problem(this, con, " There are ditag_features without a partner (start/end)!");
		result = false;

	    } else {

		ReportManager.correct(this, con, "All ditag_features have ditag partners (start/end).");

	    }
        } catch (SQLException se) {
            se.printStackTrace();
        }

        return result;
    }

    // ----------------------------------------------------------------------
    /**
     * Check that no ditags have more than 2 ditag_features with the same ditag_id & ditag_pair_id
     */

    private boolean checkForMultis(Connection con) {

        boolean result      = true;
	String analysis_ids = "";

        try {

	    //Get the analysis ids of the ditags
	    Statement stmt = con.createStatement();
	    ResultSet rs   = stmt.executeQuery("SELECT DISTINCT analysis_id FROM ditag_feature;");

	    while (rs.next()) {
		String analysis_id = rs.getString("analysis_id");
		if ( analysis_ids.length() > 0 ) {
		    analysis_ids = analysis_ids + ", " + analysis_id;
		}
		else {
		    analysis_ids = analysis_id + " ";
		}
	    }

	    if (analysis_ids.length() == 0) {
		return true;
	    }

	    //Check for ditag_ids that occur only once, ignore CAGE tags ("F")
	    String sql = "SELECT COUNT(*) AS multis FROM (select count(*) as count from ditag_feature df where analysis_id IN("
		+ analysis_ids
		+ ") and df.ditag_side!='F' group by ditag_id, ditag_pair_id having count>2 LIMIT 1) as counter;";

	    int count = 0;
	    rs        = stmt.executeQuery(sql);
	    rs.next();
	    count     = rs.getInt("multis");

	    if (count > 0) {

		ReportManager.problem(this, con, " There are ditag_features with more than two features "
				      + "in same (ditag_id/ditag_pair_id) group!.\n" + sql);
		result = false;

	    } else {

		ReportManager.correct(this, con, "All ditag_features groups have 2 partners (start/end).");

	    }
        } catch (SQLException se) {
            se.printStackTrace();
        }

        return result;
    }

}
