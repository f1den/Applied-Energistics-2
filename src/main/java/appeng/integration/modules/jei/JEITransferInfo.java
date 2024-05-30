package appeng.integration.modules.jei;

import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.gui.TooltipRenderer;
import mezz.jei.gui.recipes.RecipeLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class JEITransferInfo implements IRecipeTransferError {

    public static final JEITransferInfo INSTANCE = new JEITransferInfo();
    private RecipeLayout recipeLayout;


    private JEITransferInfo() {}

    @Override
    public @NotNull Type getType() {
        // Workaround. Re-enable the button.
        if (recipeLayout != null) {
            var recipeTransferButton =  recipeLayout.getRecipeTransferButton();
            if (recipeTransferButton != null) {
                recipeTransferButton.enabled = true;
            }
        }
        return Type.USER_FACING;
    }

    public void setRecipeLayout(RecipeLayout recipeLayout) {
        this.recipeLayout = recipeLayout;
    }

    @Override
    public void showError(@NotNull Minecraft minecraft, int mouseX, int mouseY, @NotNull IRecipeLayout recipeLayout, int recipeX, int recipeY) {
        var tooltipLines = new ArrayList<String>();
        tooltipLines.add(I18n.format("gui.tooltips.appliedenergistics2.PartialTransfer"));
        tooltipLines.add(I18n.format("gui.tooltips.appliedenergistics2.CondenseItems"));
        TooltipRenderer.drawHoveringText(minecraft, tooltipLines, mouseX, mouseY);
    }
}
