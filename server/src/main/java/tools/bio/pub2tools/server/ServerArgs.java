/*
 * Copyright © 2023 Erik Jaaniso
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

package tools.bio.pub2tools.server;

import java.io.File;

import org.edamontology.edammap.server.ServerArgsBase;
import org.edamontology.pubfetcher.core.common.Arg;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;

public class ServerArgs extends ServerArgsBase {

	static final String biotoolsId = "biotools";
	private static final String biotoolsDescription = "Path of the bio.tools existing content file in JSON format; will be automatically fetched and periodically updated";
	private static final String biotoolsDefault = null;
	@Parameter(names = { "--" + biotoolsId }, required = true, description = biotoolsDescription)
	private String biotools;

	@ParametersDelegate
	private ServerPrivateArgs serverPrivateArgs = new ServerPrivateArgs();

	@Override
	protected void addArgs() {
		super.addArgs();
		args.add(new Arg<>(this::getBiotoolsFilename, null, biotoolsDefault, biotoolsId, "bio.tools file", biotoolsDescription, null, "https://bio.tools"));
	}

	@Override
	public String getLabel() {
		return "Pub2Tools-Server";
	}

	public String getBiotools() {
		return biotools;
	}
	public String getBiotoolsFilename() {
		return new File(biotools).getName();
	}

	public ServerPrivateArgs getServerPrivateArgs() {
		return serverPrivateArgs;
	}
}
