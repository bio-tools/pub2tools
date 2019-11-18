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

package tools.bio.pub2tools;

public enum Confidence {
	high("high"),
	medium("medium"),
	low("low"),
	very_low("very low");

	private final String name;

	private Confidence(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return name;
	}
}
