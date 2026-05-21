package com.mkac.meikomms;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.mkac.meikomms.ui.checksheet.ChecksheetFragment;
import com.mkac.meikomms.ui.home.HomeFragment;
import com.mkac.meikomms.ui.setting.SettingFragment;
import com.mkac.meikomms.ui.workorder.ListWorkOrderActivity;

public class ViewPagerAdapter extends FragmentStateAdapter
{
    public ViewPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position)
    {
//        switch (position)
//        {
//            case 0:
////                return new ChecksheetFragment();
//                return new HomeFragment();
//            case 1:
//                return new ChecksheetFragment();
////                return new HomeFragment();
//            case 2:
//                return new SettingFragment();
//            default:
//                return new HomeFragment();
//        }

        return new ChecksheetFragment();

       // return new ListWorkOrderActivity();
    }

    @Override
//    public int getItemCount() {
//        return 3;
//    }

    public int getItemCount() {
        return 1;
    }
}
