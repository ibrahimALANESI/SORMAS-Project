package de.symeda.sormas.app.login;

import android.databinding.BaseObservable;

/**
 * Created by Orson on 01/03/2018.
 * <p>
 * www.technologyboard.org
 * sampson.orson@gmail.com
 * sampson.orson@technologyboard.org
 */

public class LoginViewModel extends BaseObservable {

    private String userName;
    private String password;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
        //notifyPropertyChanged(BR.userName);
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
        //notifyPropertyChanged(BR.password);
    }
}
