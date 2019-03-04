import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@State(
        name="TimeTracker",
        storages = {@Storage(value = StoragePathMacros.WORKSPACE_FILE, roamingType = RoamingType.DEFAULT)}
)

final class Component implements ProjectComponent, PersistentStateComponent<States> {
    private final Project project;
    private Widget widget;

    private States lastStates = null;

    public Component(Project project) {
        this.project = project;
    }

    @Override
    public void initComponent() {
    }

    @Override
    public void disposeComponent() {
    }

    @Override
    @NotNull
    public String getComponentName() {
        return "Component";
    }

    @Override
    public void projectOpened() {
        if (widget == null) {
            widget = new Widget();
            if (lastStates != null) {
                widget.setStates(lastStates);
                lastStates = null;
            }
            WindowManager.getInstance().getStatusBar(project).addWidget(widget);
        }
    }

    @Override
    public void projectClosed() {
        if (widget != null) {
            WindowManager.getInstance().getStatusBar(project).removeWidget(widget.ID());
            lastStates = widget.getStates();
        }
    }

    @Nullable
    @Override
    public States getState() {
        if (widget != null) {
            return widget.getStates();
        } else if(lastStates != null) {
            return lastStates;
        } else {
            return new States();
        }
    }

    @Override
    public void loadState(States states) {
        if (widget != null) {
            widget.setStates(states);
        } else {
            lastStates = states;
        }
    }
}
