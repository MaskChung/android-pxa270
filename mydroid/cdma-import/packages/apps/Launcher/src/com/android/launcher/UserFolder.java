package com.android.launcher;

import android.content.Context;
import com.android.internal.provider.Settings;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;

/**
 * Folder which contains applications or shortcuts chosen by the user.
 *
 */
public class UserFolder extends Folder implements DropTarget {
    public UserFolder(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    
    /**
     * Creates a new UserFolder, inflated from R.layout.user_folder.
     *
     * @param context The application's context.
     *
     * @return A new UserFolder.
     */
    static UserFolder fromXml(Context context) {
        return (UserFolder) LayoutInflater.from(context).inflate(R.layout.user_folder, null);
    }

    public boolean acceptDrop(DragSource source, int x, int y, int xOffset, int yOffset,
            Object dragInfo) {
        final ItemInfo item = (ItemInfo) dragInfo;
        final int itemType = item.itemType;
        return (itemType == Settings.Favorites.ITEM_TYPE_APPLICATION || 
                itemType == Settings.Favorites.ITEM_TYPE_SHORTCUT) && item.container != mInfo.id;
    }

    public void onDrop(DragSource source, int x, int y, int xOffset, int yOffset, Object dragInfo) {
        final ApplicationInfo item = (ApplicationInfo) dragInfo;
        //noinspection unchecked
        ((ArrayAdapter<ApplicationInfo>) mContent.getAdapter()).add((ApplicationInfo) dragInfo);
        LauncherModel.addOrMoveItemInDatabase(mLauncher, item, mInfo.id, 0, 0, 0);
    }

    public void onDragEnter(DragSource source, int x, int y, int xOffset, int yOffset, Object dragInfo) {
    }

    public void onDragOver(DragSource source, int x, int y, int xOffset, int yOffset, Object dragInfo) {
    }

    public void onDragExit(DragSource source, int x, int y, int xOffset, int yOffset, Object dragInfo) {
    }

    @Override
    public boolean onLongClick(View v) {
        mLauncher.closeFolder(this);
        mLauncher.showRenameDialog((UserFolderInfo) mInfo);
        return true;
    }

    @Override
    public void onDropCompleted(View target, boolean success) {
        if (success) {
            //noinspection unchecked
            ArrayAdapter<ApplicationInfo> adapter =
                    (ArrayAdapter<ApplicationInfo>) mContent.getAdapter();
            adapter.remove(mDragItem);
        }
    }

    void bind(UserFolderInfo info) {
        mInfo = info;
        setContentAdapter(new ApplicationsAdapter(mContext, info.contents));
        mCloseButton.setText(info.title);
    }

    // When the folder opens, we need to refresh the GridView's selection by
    // forcing a layout
    @Override
    void onOpen() {
        super.onOpen();
        requestFocus();
    }
}
