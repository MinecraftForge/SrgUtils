/*
 * SRG Utils
 * Copyright (c) 2021
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.srgutils;

import net.minecraftforge.srgutils.IMappingFile.IClass;
import net.minecraftforge.srgutils.IMappingFile.IField;
import net.minecraftforge.srgutils.IMappingFile.IMethod;
import net.minecraftforge.srgutils.IMappingFile.IPackage;
import net.minecraftforge.srgutils.IMappingFile.IParameter;

/**
 * Implementation of {@link IRenamer} used to rename all types of nodes in mapping
 */
public class CompleteRenamer implements IRenamer
{

    public CompleteRenamer(IMappingFile reference) {
        this.reference = reference;
    }

    private final IMappingFile reference;

    @Override
    public String rename(IPackage value) {
        IPackage pkg = reference.getPackage(value.getOriginal());
        return pkg == null ? value.getMapped() : pkg.getMapped();
    }

    @Override
    public String rename(IClass value) {
        IClass cls = reference.getClass(value.getOriginal());
        return cls == null ? value.getMapped() : cls.getMapped();
    }

    @Override
    public String rename(IField value) {
        IClass cls = reference.getClass(value.getParent().getOriginal());
        IField fld = cls == null ? null : cls.getField(value.getOriginal());
        return fld == null ? value.getMapped() : fld.getMapped();
    }

    @Override
    public String rename(IMethod value) {
        IClass cls = reference.getClass(value.getParent().getOriginal());
        IMethod mtd = cls == null ? null : cls.getMethod(value.getOriginal(), value.getDescriptor());
        return mtd == null ? value.getMapped() : mtd.getMapped();
    }

    @Override
    public String rename(IParameter value) {
        IMethod mtd = value.getParent();
        IClass cls = reference.getClass(mtd.getParent().getOriginal());
        mtd = cls == null ? null : cls.getMethod(mtd.getOriginal(), mtd.getDescriptor());
        return mtd == null ? value.getMapped() : mtd.remapParameter(value.getIndex(), value.getMapped());
    }
}