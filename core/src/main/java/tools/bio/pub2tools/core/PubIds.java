/*
 * Copyright © 2019 Erik Jaaniso
 *
 * This file is part of Pub2Tools.
 *
 * Pub2Tools is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Pub2Tools is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Pub2Tools.  If not, see <http://www.gnu.org/licenses/>.
 */

package tools.bio.pub2tools.core;

import org.edamontology.pubfetcher.core.db.publication.PublicationIds;

public class PubIds {

	private String pmid;

	private String pmcid;

	private String doi;

	public String getPmid() {
		return pmid;
	}
	public void setPmid(String pmid) {
		this.pmid = pmid;
	}

	public String getPmcid() {
		return pmcid;
	}
	public void setPmcid(String pmcid) {
		this.pmcid = pmcid;
	}

	public String getDoi() {
		return doi;
	}
	public void setDoi(String doi) {
		this.doi = doi;
	}

	public PubIds() {}

	public PubIds(String pmid, String pmcid, String doi) {
		this.pmid = pmid;
		this.pmcid = pmcid;
		this.doi = doi;
	}

	@Override
	public String toString() {
		return "[" + PublicationIds.toString(pmid, pmcid, doi, false) + "]";
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof PubIds)) return false;
		PubIds other = (PubIds) obj;
		if (pmid == null) {
			if (other.pmid != null) return false;
		} else if (!pmid.equals(other.pmid)) return false;
		if (pmcid == null) {
			if (other.pmcid != null) return false;
		} else if (!pmcid.equals(other.pmcid)) return false;
		if (doi == null) {
			if (other.doi != null) return false;
		} else if (!doi.equals(other.doi)) return false;
		return other.canEqual(this);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((pmid == null) ? 0 : pmid.hashCode());
		result = prime * result + ((pmcid == null) ? 0 : pmcid.hashCode());
		result = prime * result + ((doi == null) ? 0 : doi.hashCode());
		return result;
	}

	public boolean canEqual(Object other) {
		return (other instanceof PubIds);
	}
}
