package net.fortytwo.myotherbrain.update.actions;

import net.fortytwo.myotherbrain.model.concepts.Association;
import net.fortytwo.myotherbrain.model.concepts.Atom;
import net.fortytwo.myotherbrain.update.UpdateException;
import net.fortytwo.myotherbrain.update.WriteContext;

import java.net.URI;
import java.util.Date;

/**
 * Author: josh
 * Date: Jun 28, 2009
 * Time: 12:03:59 AM
 */
public class CreateAssociation extends CreateAtom {
    private final URI associationSubject;
    private final URI associationObject;

    public CreateAssociation(
            URI subject,
            String name,
            String description,
            String richTextDescription,
            URI icon,
            URI sensitivity,
            Float emphasis,
            Date creationTimeStamp,
            URI creationPlaceStamp,
            URI associationSubject,
            URI associationObject,
            final WriteContext c) throws UpdateException {
        super(subject, name, description, richTextDescription, icon, sensitivity, emphasis,
                creationTimeStamp, creationPlaceStamp, c);
        if (null == associationSubject) {
            throw new NullPointerException();
        } else {
            associationSubject = c.normalizeResourceURI(associationSubject);
        }

        if (null == associationObject) {
            throw new NullPointerException();
        } else {
            associationSubject = c.normalizeResourceURI(associationSubject);
        }

        this.associationSubject = associationSubject;
        this.associationObject = associationObject;
    }

    @Override
    protected void executeUndo(final WriteContext c) throws UpdateException {
        Atom subject = toThing(this.subject, Atom.class, c);
        c.remove(subject);
    }

    @Override
    protected void executeRedo(final WriteContext c) throws UpdateException {
        // TODO: is there any reason to use "designate" over "create"?
        Association subject = c.designate(toQName(this.subject), Association.class);

        setCommonValues(subject, c);

        subject.setSubject(toThing(associationSubject, Atom.class, c));
        subject.setObject(toThing(associationObject, Atom.class, c));
    }
}
