/*******************************************************************************
 * Copyright (c) 2018 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.internal.apt.pluggable.core.filer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.apt.core.internal.util.FileSystemUtil;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.apt.pluggable.core.Apt6Plugin;
import org.eclipse.jdt.internal.apt.pluggable.core.dispatch.IdeProcessingEnvImpl;
import org.eclipse.jdt.internal.compiler.Compiler;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader;
import org.eclipse.jdt.internal.compiler.env.IBinaryType;
import org.eclipse.jdt.internal.compiler.lookup.BinaryTypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;

/**
 * @see IdeClassOutputStream
 */
public class IdeClassOutputStream  extends ByteArrayOutputStream
{
	private final IdeProcessingEnvImpl _env;
	private final IFile _file;
	
	public IdeClassOutputStream(IdeProcessingEnvImpl env, IFile file) {
		_env = env;
		_file = file;
	}

	@Override
	public void close() throws IOException {
		super.close();
		byte[] byteArray = toByteArray();
		InputStream contents = new ByteArrayInputStream(byteArray);
		Compiler compiler = this._env.getCompiler();
		
		IBinaryType binaryType = null;
		try {
			binaryType = ClassFileReader.read(this._file.getLocation().toString());
			if (binaryType == null) {
				saveToDisk(contents, true);
			} else {
				saveToDisk(contents, false);
			}
			binaryType = ClassFileReader.read(this._file.getLocation().toString());
			char[] name = binaryType.getName();
			ReferenceBinding type = compiler.lookupEnvironment.getType(CharOperation.splitOn('/', name));
			if (type != null && type.isValidBinding()) {
				if (type.isBinaryBinding()) {
					_env.addNewClassFile(type);
				} else {
					BinaryTypeBinding binaryBinding = new BinaryTypeBinding(type.getPackage(), binaryType, compiler.lookupEnvironment, true);
					if (binaryBinding != null)
						_env.addNewClassFile(binaryBinding);
				}
			}
		} catch(Exception ex) {
			// move on
		}
		finally {
			closeInputStream(contents);
		}
	}
	
	private void closeInputStream(InputStream stream) {
		if (stream != null) {
			try {
				stream.close();
			}
			catch (IOException ioe) {}
		}
	}
	private void saveToDisk(InputStream toSave, boolean create) throws IOException{
		try {
			FileSystemUtil.makeDerivedParentFolders(_file.getParent());
			if (create) {
				_file.create(toSave, IResource.FORCE | IResource.DERIVED, null);
			}
			else {
				_file.setContents(toSave, true, false, null);
			}
		}
		catch (CoreException ce) {
			if (_file.exists()) {
				// Do nothing. This is a case-insensitive file system mismatch,
				// and the underlying platform has saved the contents already.
			}
			else {
				Apt6Plugin.log(ce, "Could not create generated non-Java file " + _file.getName()); //$NON-NLS-1$
				throw new IOException(ce);
			}
		}
	}
}
