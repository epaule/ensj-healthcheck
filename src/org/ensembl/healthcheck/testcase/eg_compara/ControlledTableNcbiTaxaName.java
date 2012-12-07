package org.ensembl.healthcheck.testcase.eg_compara;

import org.ensembl.healthcheck.testcase.AbstractControlledTable;

public class ControlledTableNcbiTaxaName extends AbstractControlledTable {
	
	@Override protected String getControlledTableName() {
		return "ncbi_taxa_name";	
	}
	
	@Override protected ComparisonStrategy getComparisonStrategy() {
		return ComparisonStrategy.Checksum;
	}
}
