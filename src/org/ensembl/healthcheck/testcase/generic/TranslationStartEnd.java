/*
 * Copyright (C) 2004 EBI, GRL
 * 
 * This library is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation; either version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along with
 * this library; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */

package org.ensembl.healthcheck.testcase.generic;

import java.sql.Connection;

import org.ensembl.healthcheck.DatabaseRegistryEntry;
import org.ensembl.healthcheck.DatabaseType;
import org.ensembl.healthcheck.ReportManager;
import org.ensembl.healthcheck.Team;
import org.ensembl.healthcheck.testcase.SingleDatabaseTestCase;
import org.ensembl.healthcheck.util.DBUtils;

/**
 * Check that if the start and end of translation is on the same exon, that start < end. Also check that translation ends aren't
 * beyond exon ends.
 */
public class TranslationStartEnd extends SingleDatabaseTestCase {

	/**
	 * Creates a new instance of CheckTranslationStartEnd
	 */
	public TranslationStartEnd() {
		
		addToGroup("post_genebuild");
		addToGroup("pre-compara-handover");
		addToGroup("post-compara-handover");
		
		setDescription("Check that if the start and end of translation is on the same exon, that start < end. Also check that translation ends aren't beyond exon ends.");
		setTeamResponsible(Team.GENEBUILD);
	}

	/**
	 * This only applies to core and Vega databases.
	 */
	public void types() {

		removeAppliesToType(DatabaseType.OTHERFEATURES);
		removeAppliesToType(DatabaseType.RNASEQ);

	}

	/**
	 * Find any matching databases that have start > end.
	 * 
	 * @param dbre
	 *          The database to use.
	 * @return Result.
	 */
	public boolean run(DatabaseRegistryEntry dbre) {

		boolean result = true;

		// check start < end
		Connection con = dbre.getConnection();
		int rows = DBUtils.getRowCount(con, "SELECT COUNT(translation_id) FROM translation WHERE start_exon_id = end_exon_id AND seq_start > seq_end");
		if (rows > 0) {
			result = false;
			ReportManager.problem(this, con, rows + " translations have start > end");
		} else {
			ReportManager.correct(this, con, "No translations have start > end");
		}

		// check no translations overrun their exons
		rows = DBUtils.getRowCount(con, "SELECT COUNT(*) FROM translation t, exon e WHERE t.end_exon_id=e.exon_id AND e.seq_region_end-e.seq_region_start+1 < t.seq_end");
		if (rows > 0) {
			result = false;
			ReportManager.problem(this, con, rows + " translations end beyond the end of their exons");
		} else {
			ReportManager.correct(this, con, "No translations overrun exons");
		}

                // check the start and end exon have a correct phase
                rows = DBUtils.getRowCount(con, "SELECT COUNT(*) FROM translation t, exon e WHERE t.start_exon_id=e.exon_id AND start_exon_id <> end_exon_id and end_phase = -1");
                if (rows > 0) {
                        result = false;
                        ReportManager.problem(this, con, rows + " translations have start exon with a -1 end phase");
                } else {
                        ReportManager.correct(this, con, "Start exons for translations have correct end phase");
                }

                rows = DBUtils.getRowCount(con, "SELECT COUNT(*) FROM translation t, exon e WHERE t.end_exon_id=e.exon_id AND start_exon_id <> end_exon_id and phase = -1");
                if (rows > 0) {
                        result = false;
                        ReportManager.problem(this, con, rows + " translations have end exon with -1 phase");
                } else {
                        ReportManager.correct(this, con, "End exons for translations have correct phase");
                }


		return result;

	} // run

} // TranslationStartEnd
