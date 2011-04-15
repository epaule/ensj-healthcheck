/*
 * Copyright (C) 2004 EBI, GRL
 * 
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation; either version 2.1 of the License, or (at your
 * option) any later version.
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package org.ensembl.healthcheck.testcase.generic;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.ensembl.healthcheck.DatabaseRegistryEntry;
import org.ensembl.healthcheck.ReportManager;
import org.ensembl.healthcheck.testcase.SingleDatabaseTestCase;

/**
 * Check that the assembly table and seq_region table are consistent.
 */
public class AssemblySeqregion extends SingleDatabaseTestCase {

    /**
     * Create a new AssemlySeqregion test case.
     */
    public AssemblySeqregion() {

        addToGroup("post_genebuild");
        addToGroup("release");
		addToGroup("compara-ancestral");
        setDescription("Check that the chromosome lengths from the seq_region table agree with both the assembly table and the karyotype table.");
        setTeamResponsible("GeneBuilders and Core");

    }

    /**
     * @param dbre The database to use.
     * @return The test case result.
     */
    public boolean run(DatabaseRegistryEntry dbre) {

        boolean result = true;

        Connection con = dbre.getConnection();

        // ---------------------------------------------------
        // Find any seq_regions that have different lengths in seq_region & assembly
        // NB seq_region length should always be equal to (or possibly greater than) the maximum
        // assembly length
        // The SQL returns failures
        // ----------------------------------------------------
        String sql = "SELECT sr.name AS name, sr.length, cs.name AS coord_system "
                + "FROM seq_region sr, assembly ass, coord_system cs " + "WHERE sr.coord_system_id=cs.coord_system_id "
                + "AND ass.asm_seq_region_id = sr.seq_region_id " + "GROUP BY ass.asm_seq_region_id "
                + "HAVING sr.length < MAX(ass.asm_end)";

        try {

            Statement stmt = con.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            int i = 0;
            while (rs.next() && i++ < 50) {
                result = false;
                String cs = rs.getString("coord_system");
                String sr = rs.getString("name");
                ReportManager.problem(this, con, cs + " " + sr + " is shorter in seq_region than in assembly");
            }
            if (i == 0) {
                ReportManager.correct(this, con,
                        "Sequence region lengths are equal or greater in the seq_region table compared to the assembly table");
            }
        } catch (SQLException e) {
            System.err.println("Error executing " + sql + ":");
            e.printStackTrace();
        }

        // -------------------------------------------------------
        // check various other things about the assembly table
        // Check for mismatched lengths of assembled and component sides.
        // ie where (asm_end - asm_start + 1) != (cmp_end - cmp_start + 1)
        int rows = getRowCount(con, "SELECT COUNT(*) FROM assembly WHERE (asm_end - asm_start + 1) != (cmp_end - cmp_start + 1)");
        if (rows > 0) {
            ReportManager.problem(this, con, rows
                    + " rows in assembly table have mismatched lengths of assembled and component sides");
        } else {
            ReportManager.correct(this, con, "All rows in assembly table have matching lengths of assembled and component sides");
        }

        // check for start/end < 1
        rows = getRowCount(con, "SELECT COUNT(*) FROM assembly WHERE asm_start < 1 OR asm_end < 1 OR cmp_start < 1 OR cmp_end < 1");
        if (rows > 0) {
            ReportManager.problem(this, con, rows + " rows in assembly table have start or end coords < 1");
        } else {
            ReportManager.correct(this, con, "All rows in assembly table have start and end coords > 0");
        }

        // check for end < start
        rows = getRowCount(con, "SELECT COUNT(*) FROM assembly WHERE asm_end < asm_start OR cmp_end < cmp_start");
        if (rows > 0) {
            ReportManager.problem(this, con, rows + " rows in assembly table have start or end coords < 1");
        } else {
            ReportManager.correct(this, con, "All rows in assembly table have end coords > start coords");
        }

        return result;

    } // run

} // ChromosomeLengths
